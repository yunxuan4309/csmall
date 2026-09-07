package com.cooxiao.mall.seckill.task;

import com.cooxiao.mall.pojo.seckill.model.SeckillSku;
import com.cooxiao.mall.seckill.mapper.SeckillSkuMapper;
import com.cooxiao.mall.seckill.utils.SeckillCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀库存对账任务（TODO #14-P1）。
 *
 * 背景：秒杀走"Redis=闸门、DB=账本"模型。下单时 Redis DECR 预扣（mall:seckill:sku:stock:{skuId}），
 * 异步 MQ 消费时 DB 条件扣减（seckill_sku.seckill_stock>=qty，防超卖）。两者异步，任一侧失败都会让
 * Redis 与 DB 库存值漂移：
 *   - Redis 丢预扣/被动过    → Redis 偏大（扣少了）→ 可能超卖
 *   - Redis 多放行但 DB 未成交 → Redis 偏小（多放） → 有货不能买
 *
 * 对账目标：以 DB 为唯一可信基准（账本），把 Redis 预扣闸门修正回 DB 值，让漂移在用户感知前自愈。
 *
 * 设计（业界分层，见方案文档）：分两层调度，复用同一 reconcileAll(online) 核心，仅范围/阈值不同：
 *   - 运行期轻量纠偏 reconcileOnline()：每 5 分钟，只处理已预热的 sku（Redis 有 key），
 *     容错瞬时差（|diff|>=2 或 |diff|==1 连续同向差满 3 次才修），避免误改 Redis 刚 DECR、MQ 还未扣的合法差。
 *   - 凌晨全量校准 reconcileDaily()：每日凌晨 cron，全量比对所有 sku，|diff|>=1 即修（低峰无并发），
 *     并补建 Redis key（预热未跑/已过期）到 DB 值，保证开抢前 Redis 就绪。
 *
 * 说明：对账回补在 MQ 积压时可能短暂让 Redis 大于真实可成交数，但最终仍被 DB 条件扣减
 *       seckill_stock>=qty 拦下（rows==0 不成交），不会真正超卖——只是从"拦在 Redis"退化到"拦在 DB"。
 */
@Slf4j
@Component
public class SeckillReconcileTask {

    /** 运行期 |diff| 阈值：≥此值直接修正（±1 视为合法单笔追赶，交给下一次对账收敛） */
    @Value("${seckill.reconcile.online-threshold:2}")
    private int onlineThreshold;

    /** 运行期连续同向差次数阈值：|diff|==1 时连续满 N 次才修（规避瞬时差） */
    @Value("${seckill.reconcile.online-consecutive:3}")
    private int onlineConsecutive;

    /** 连续计数的 Redis key TTL（分钟），避免长期占用 */
    @Value("${seckill.reconcile.counter-ttl-minutes:30}")
    private long counterTtlMinutes;

    private static final String RECONCILE_CNT_PREFIX = "mall:seckill:reconcile:cnt:";

    @Autowired
    private SeckillSkuMapper seckillSkuMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 运行期轻量纠偏（每 5 分钟） */
    @Scheduled(fixedDelayString = "${seckill.reconcile.online-fixed-delay:300000}")
    public void reconcileOnline() {
        try {
            reconcileAll(true);
        } catch (Exception e) {
            // 对账是兜底任务，绝不能因异常冒泡影响主链路；失败仅告警留痕
            log.error("【对账-运行期】执行异常: {}", e.getMessage(), e);
        }
    }

    /** 凌晨全量校准（默认 03:30，低峰执行） */
    @Scheduled(cron = "${seckill.reconcile.daily-cron:0 30 3 * * ?}")
    public void reconcileDaily() {
        try {
            log.info("【对账-凌晨校准】开始执行全量校准");
            reconcileAll(false);
            log.info("【对账-凌晨校准】执行完成");
        } catch (Exception e) {
            log.error("【对账-凌晨校准】执行异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 对账核心：比对所有秒杀 sku 的 Redis 与 DB 库存值，以 DB 为准修正 Redis。
     *
     * @param online true=运行期（容错瞬时差 + 只处理已预热 sku）；false=凌晨全量（|diff|>=1 即修 + 补建缺失 key）
     */
    private void reconcileAll(boolean online) {
        List<SeckillSku> skus = seckillSkuMapper.selectList(null);
        if (skus == null || skus.isEmpty()) {
            return;
        }
        for (SeckillSku sku : skus) {
            Long skuId = sku.getSkuId();
            if (skuId == null) {
                continue;
            }
            String stockKey = SeckillCacheUtils.getStockKey(skuId);
            Integer dbValue = sku.getSeckillStock();

            String redisStr = stringRedisTemplate.boundValueOps(stockKey).get();
            if (redisStr == null) {
                // Redis 无 key：
                //  运行期 → 跳过（未预热/已过期，交给预热重建，避免提前建 key 让它"看起来有货"）；
                //  凌晨全量 → 低峰补建到 DB 值，保证开抢前就绪
                if (!online && dbValue != null && dbValue > 0) {
                    stringRedisTemplate.boundValueOps(stockKey).set(dbValue + "");
                    log.info("【对账-凌晨校准】补建 Redis 库存 key, skuId={}, DB={}", skuId, dbValue);
                }
                continue;
            }

            long redisValue;
            try {
                redisValue = Long.parseLong(redisStr);
            } catch (NumberFormatException e) {
                log.warn("【对账】Redis 库存值非法（非数字），跳动, skuId={}, value={}", skuId, redisStr);
                continue;
            }

            long diff = redisValue - (dbValue == null ? 0L : dbValue.longValue());
            if (diff == 0) {
                resetDiffCounter(skuId);
                continue;
            }

            boolean shouldFix;
            if (online) {
                // 运行期容错：|diff|>=threshold 直接修；|diff|==1 连续同向差满N次才修（规避瞬时追赶）
                if (Math.abs(diff) >= onlineThreshold) {
                    shouldFix = true;
                } else if (Math.abs(diff) == 1) {
                    shouldFix = shouldFixAfterConsecutive(skuId, Long.signum(diff));
                } else {
                    shouldFix = false;
                }
            } else {
                // 凌晨全量：低峰无并发，|diff|>=1 即修，彻底对齐
                shouldFix = true;
            }

            if (shouldFix) {
                fixStock(skuId, stockKey, redisValue, dbValue == null ? 0L : dbValue.longValue(), diff);
            }
        }
    }

    /**
     * 以 DB 为权威覆盖 Redis 库存值。
     */
    private void fixStock(Long skuId, String stockKey, long redisValue, long dbValue, long diff) {
        stringRedisTemplate.boundValueOps(stockKey).set(dbValue + "");
        resetDiffCounter(skuId);
        log.warn("【秒杀库存对账】skuId={}, Redis={}, DB={}, 差异={}, 已按DB修正为 {}",
                skuId, redisValue, dbValue, diff, dbValue);
    }

    /**
     * 记录连续同向差次数，连续满 N 次（同方向）才判定为真实漂移。
     * 用 Redis 计数（key=mall:seckill:reconcile:cnt:{skuId}:{dir}），短 TTL，兼容未来集群化。
     *
     * @return true 表示该 sku 连续同一方向差已满 onlineConsecutive 次，应修正
     */
    private boolean shouldFixAfterConsecutive(Long skuId, int direction) {
        String cntKey = RECONCILE_CNT_PREFIX + skuId + ":" + direction;
        String cntStr = stringRedisTemplate.boundValueOps(cntKey).get();
        long cnt = 1;
        if (cntStr != null) {
            try {
                cnt = Long.parseLong(cntStr) + 1;
            } catch (NumberFormatException e) {
                cnt = 1;
            }
        }
        stringRedisTemplate.boundValueOps(cntKey).set(cnt + "", counterTtlMinutes, TimeUnit.MINUTES);
        return cnt >= onlineConsecutive;
    }

    /** 一致/已修正后清累计计数 */
    private void resetDiffCounter(Long skuId) {
        try {
            stringRedisTemplate.delete(RECONCILE_CNT_PREFIX + skuId + ":1");
            stringRedisTemplate.delete(RECONCILE_CNT_PREFIX + skuId + ":-1");
        } catch (Exception e) {
            log.debug("清理对账计数失败, skuId={}: {}", skuId, e.getMessage());
        }
    }
}

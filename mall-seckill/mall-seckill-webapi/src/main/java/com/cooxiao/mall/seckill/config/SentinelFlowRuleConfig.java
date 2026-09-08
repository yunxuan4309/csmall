package com.cooxiao.mall.seckill.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 流控规则配置
 * 秒杀提交接口限制QPS，防止高并发压垮服务
 *
 * ⚠️ 规则来源说明（TODO #5，2026-09-08 复核修正）：
 * - 运行期权威源 = Nacos（prod yml datasource.flow.nacos → mall-seckill-flow-rules@SENTINEL_GROUP），
 *   支持 Nacos/控制台热更新、重启恢复。
 * - 本类代码 loadRules 为【启动瞬态 + Nacos 不可达兜底】：应用启动先加载本地规则，
 *   随后 Nacos datasource 若成功拉到配置（含空）会整体替换本地（FlowRuleManager 整体替换非叠加）。因此：
 *   · Nacos 正常有规则 → 以 Nacos 为准（本地被覆盖为同值/新值，行为一致）；
 *   · Nacos 不可达（网络断/宕机）→ datasource 拉取失败不推送 → 本地规则存活 = 兜底有效；
 *   · Nacos 可达但配置为空 → 本地规则被擦除（历史坑：日志 "converter can not convert ... source is empty"）→
 *     兜底对"配置被误删"场景无效，靠规则 JSON 入库（deploy/docker/sentinel/）+ 部署清单建规则 + 巡检防复发。
 */
@Configuration
@Slf4j
public class SentinelFlowRuleConfig {

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        // 秒杀订单提交接口限流：每秒最多允许10个请求通过
        FlowRule seckillRule = new FlowRule();
        seckillRule.setResource("秒杀订单提交");
        seckillRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        seckillRule.setCount(10);
        seckillRule.setLimitApp("default");
        rules.add(seckillRule);

        FlowRuleManager.loadRules(rules);
        log.info("Sentinel流控规则加载完成(启动兜底): 秒杀订单提交QPS限制为10；运行期以 Nacos mall-seckill-flow-rules 为准");
    }
}

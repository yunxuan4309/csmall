package com.cooxiao.mall.seckill.service.order;

/**
 * 秒杀成交状态查询服务（供 order 模块支付前校验 —— TODO #14 P0 方案Y）
 *
 * <p>背景：秒杀订单付款前需确认"本单是否已成功落库"（success 表有记录）。
 * 若 Redis 预扣放行但 DB 条件扣减失败（rows==0，如 Redis 与 DB 库存不一致），
 * 该单不会写入 success —— 此时应拦截支付，防"付了钱没货"。
 *
 * <p>查"本单成交状态"而非"剩余库存"的原因：DB 扣减逐单进行，成交单必有
 * success 记录；查剩余库存会误拦已成交的最后一件（其 DB 库存已归零但 success 存在）。
 */
public interface IForOrderSeckillRecordService {

    /**
     * 查询秒杀订单是否已成功落库（success 表是否存在该 orderSn）
     *
     * @param orderSn 订单编号（oms_order.sn）
     * @return true=已落库（可支付）；false=未落库（MQ 扣减失败/尚未消费，应拦支付）
     */
    boolean isSeckillSuccessRecorded(String orderSn);
}

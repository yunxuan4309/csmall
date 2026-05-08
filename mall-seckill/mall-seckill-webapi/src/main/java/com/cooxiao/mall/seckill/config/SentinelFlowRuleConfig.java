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
 * Sentinel流控规则配置
 * 秒杀提交接口限制QPS，防止高并发压垮服务
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
        log.info("Sentinel流控规则加载完成: 秒杀订单提交QPS限制为10");
    }
}

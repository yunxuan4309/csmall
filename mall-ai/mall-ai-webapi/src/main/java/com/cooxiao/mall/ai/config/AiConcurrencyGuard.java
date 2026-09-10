package com.cooxiao.mall.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * AI 并发闸门（TODO #2+#34）
 *
 * 背景：AI 高并发 ≠ 秒杀高并发——秒杀是大量快请求（限流削峰可控），
 * AI 是【少量慢请求占 Tomcat 线程 + 外部 LLM API 配额有限】。
 * 不设闸门时：每个 SSE/问答请求秒级挂一个线程，少量用户即可占满线程池拖垮其他接口；
 * 且 LLM API 是外部共享资源（QPS 配额 + token 收费），不能无限并发调用。
 *
 * 机制：Semaphore 限制"同时进行的 LLM 调用数"（默认 20）。
 * - tryAcquire 成功 → 进入真实 LLM 调用，finally 释放
 * - tryAcquire 失败（闸门满）→ 抛 {@link AiBusyException} → 由各 service 既有降级路径处理
 *   （Search 返回纯 ES 结果、Ask 返回 busy、SSE 发 error 事件）= 降级而非 500
 *
 * 挂点：所有真实 LLM 调用的汇聚点——
 *   DeepSeekAiClient.chat(task, ...)（同步；覆盖 chat / json / expand / compare 各任务）
 *   DeepSeekAiClient.streamChat（SSE 流式，2026-09-11 由 ChatServiceImpl 收敛进来）
 *   SiliconFlowEmbeddingClient.embed / embedBatch（embedding）
 */
@Slf4j
@Component
public class AiConcurrencyGuard {

    private Semaphore semaphore;

    @Autowired
    private AiProperties aiProperties;

    @PostConstruct
    void init() {
        semaphore = new Semaphore(Math.max(1, aiProperties.getConcurrentMax()));
        log.info("AI 并发闸门初始化：最大并发 LLM 调用数 = {}", aiProperties.getConcurrentMax());
    }

    @PreDestroy
    void destroy() {
        log.info("AI 并发闸门关闭");
    }

    /** 当前可用许可数（监控/日志用） */
    public int availablePermits() {
        return semaphore == null ? -1 : semaphore.availablePermits();
    }

    /** 当前已占用并发数 */
    public int occupied() {
        return semaphore == null ? 0
                : Math.max(0, aiProperties.getConcurrentMax() - semaphore.availablePermits());
    }

    /**
     * 尝试获取并发许可。失败抛 {@link AiBusyException}（调用方应降级而非让请求失败）。
     *
     * @param scene 场景名（日志用，如 "chat" / "search" / "ask" / "stream"）
     */
    public void acquire(String scene) {
        if (semaphore == null) {
            return;
        }
        int waitMs = aiProperties.getConcurrentWaitMs();
        boolean acquired;
        try {
            if (waitMs > 0) {
                acquired = semaphore.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
            } else {
                acquired = semaphore.tryAcquire();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiBusyException("AI 并发闸门被中断", e);
        }
        if (!acquired) {
            log.warn("【AI并发闸门】{} 场景繁忙：当前并发 {}/{}，已拒绝本次调用",
                    scene, occupied(), aiProperties.getConcurrentMax());
            throw new AiBusyException("AI 服务繁忙，请稍后再试");
        }
    }

    /** 释放并发许可（必须与 acquire 成对，finally 中调用） */
    public void release() {
        if (semaphore != null) {
            semaphore.release();
        }
    }
}

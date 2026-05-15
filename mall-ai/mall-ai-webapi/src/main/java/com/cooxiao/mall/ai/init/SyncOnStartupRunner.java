package com.cooxiao.mall.ai.init;

import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.ai.service.impl.VectorSyncServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时自动同步商品数据到 ES
 * 生产环境通过 cooxiao.ai.sync-auto-on-startup=true 开启
 */
@Slf4j
@Component
public class SyncOnStartupRunner implements ApplicationRunner {

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private VectorSyncServiceImpl vectorSyncService;

    @Override
    public void run(ApplicationArguments args) {
        if (!aiProperties.isSyncAutoOnStartup()) {
            return;
        }
        log.info("启动自动同步：开始同步商品数据到 ES...");
        try {
            int count = vectorSyncService.syncAll();
            log.info("启动自动同步完成，共 {} 条商品", count);
        } catch (Exception e) {
            log.error("启动自动同步失败，RAG 问答可能无数据", e);
        }
    }
}

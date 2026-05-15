package com.cooxiao.mall.ai.service.impl;

import com.cooxiao.mall.ai.service.ISpuSyncService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * SPU 同步 Dubbo 服务提供者
 * mall-product 通过 Dubbo 调用此服务，在商品变更后自动同步到 ES
 */
@Slf4j
@DubboService
public class SpuSyncDubboServiceImpl implements ISpuSyncService {

    @Autowired
    private VectorSyncServiceImpl vectorSyncService;

    @Override
    public void syncSpu(Long spuId) {
        log.info("收到 Dubbo 同步请求: spuId={}", spuId);
        vectorSyncService.syncSpu(spuId);
    }
}

package com.cooxiao.mall.ai.service;

/**
 * SPU 数据同步 Dubbo 服务接口
 * 供 mall-product 模块在商品新增/更新后调用，将数据同步到 ES
 */
public interface ISpuSyncService {

    /**
     * 同步单个 SPU 到 ES（新增或更新时触发）
     *
     * @param spuId SPU ID
     */
    void syncSpu(Long spuId);
}

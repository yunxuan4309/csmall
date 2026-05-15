package com.cooxiao.mall.ai.service;

import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.pojo.ai.vo.CompareResultVO;

/**
 * AI 智能导购 Dubbo 服务接口
 */
public interface IAiService {

    /**
     * AI 商品对比
     *
     * @param spuIds     对比的 SPU ID 列表
     * @param dimensions 对比维度（可选）
     * @return 结构化对比结果
     */
    JsonResult<CompareResultVO> compareProducts(java.util.List<Long> spuIds,
                                                java.util.List<String> dimensions);
}

package com.cooxiao.mall.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.cooxiao.mall.ai.client.AiClient;
import com.cooxiao.mall.ai.service.TokenBudgetService;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.ai.vo.CompareResultVO;
import com.cooxiao.mall.pojo.ai.vo.ProductCompareItemVO;
import com.cooxiao.mall.pojo.product.vo.SpuStandardVO;
import com.cooxiao.mall.product.service.front.IForFrontSpuService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品对比服务实现
 */
@Slf4j
@Service
public class ProductCompareServiceImpl {

    @DubboReference
    private IForFrontSpuService spuService;

    @Autowired
    private AiClient aiClient;

    @Autowired
    private TokenBudgetService tokenBudgetService;

    /** 默认对比维度 */
    private static final List<String> DEFAULT_DIMENSIONS = List.of(
            "规格参数", "价格", "核心卖点", "适用场景");

    /**
     * AI 商品对比
     * 维度值由后端从商品字段直接填充（不靠 AI，杜绝重复），AI 只生成综合总结
     */
    public JsonResult<CompareResultVO> compare(List<Long> spuIds, List<String> dimensions) {
        // 1. 通过 Dubbo 获取各 SPU 详情
        List<SpuStandardVO> spuList = new ArrayList<>();
        for (Long spuId : spuIds) {
            SpuStandardVO spu = spuService.getSpuById(spuId);
            if (spu != null) {
                spuList.add(spu);
            }
        }

        if (spuList.isEmpty()) {
            return failedResult(ResponseCode.NOT_FOUND, "未找到对应的商品信息");
        }

        // 2. 确定对比维度
        List<String> actualDimensions = (dimensions != null && !dimensions.isEmpty())
                ? dimensions : DEFAULT_DIMENSIONS;

        // 3. 预算检查
        if (tokenBudgetService.isBudgetExceeded()) {
            log.warn("今日 AI 预算已超限，拒绝商品对比请求");
            return failedResult(ResponseCode.INTERNAL_SERVER_ERROR, "服务繁忙，请稍后再试");
        }

        // 4. 从商品字段填充维度值（不靠 AI，稳定不重复）
        CompareResultVO result = new CompareResultVO();
        result.setDimensions(actualDimensions);
        result.setProducts(spuList.stream()
                .map(spu -> buildCompareItem(spu, actualDimensions))
                .collect(Collectors.toList()));

        // 5. AI 只生成总结
        try {
            String summary = aiClient.chat(buildSummaryPrompt(spuList),
                    "请简洁总结对比并给出推荐。");
            result.setSummary(summary);
        } catch (Exception e) {
            log.error("AI 总结生成失败", e);
            result.setSummary("AI 服务暂时不可用，请稍后重试");
        }

        return JsonResult.ok(result);
    }

    /** 从 SPU 字段直接填充维度值 */
    private ProductCompareItemVO buildCompareItem(SpuStandardVO spu, List<String> dimensions) {
        ProductCompareItemVO vo = new ProductCompareItemVO();
        vo.setId(spu.getId());
        vo.setName(spu.getName());

        // 提取第一张图片
        String pictures = spu.getPictures();
        if (pictures != null && !pictures.isBlank()) {
            try {
                JSONArray pics = JSON.parseArray(pictures);
                if (!pics.isEmpty()) vo.setPicture(pics.getString(0));
            } catch (Exception e) {
                vo.setPicture(pictures);
            }
        }

        List<String> values = new ArrayList<>();
        for (String dim : dimensions) {
            values.add(switch (dim) {
                case "价格" -> spu.getListPrice() != null
                        ? spu.getListPrice() + "元" : "暂无";
                case "规格参数" -> fallback(spu.getKeywords(), spu.getTitle());
                case "核心卖点" -> fallback(spu.getTags(), spu.getDescription());
                case "适用场景" -> inferScenario(spu);
                case "品牌" -> fallback(spu.getBrandName(), "暂无");
                default -> nullSafe(spu.getTitle());
            });
        }
        vo.setDimensionValues(values);
        return vo;
    }

    private String inferScenario(SpuStandardVO spu) {
        String tags = nullSafe(spu.getTags());
        if (tags.contains("游戏") || tags.contains("电竞")) return "游戏玩家";
        if (tags.contains("拍照") || tags.contains("影像")) return "摄影爱好者";
        if (tags.contains("学生") || tags.contains("性价比")) return "学生党、性价比用户";
        if (tags.contains("商务") || tags.contains("办公")) return "商务办公";
        if (spu.getListPrice() != null && spu.getListPrice().doubleValue() > 5000) return "高端用户";
        return "日常使用";
    }

    private String buildSummaryPrompt(List<SpuStandardVO> spuList) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个电商导购。请对比以下商品并给出 150 字以内的综合推荐建议。\n\n");
        for (SpuStandardVO spu : spuList) {
            sb.append(spu.getName())
              .append("（").append(spu.getListPrice()).append("元，").append(spu.getBrandName()).append("）")
              .append(" - ").append(nullSafe(spu.getDescription())).append("\n");
        }
        return sb.toString();
    }

    private String nullSafe(String s) {
        return s == null || s.isBlank() ? "暂无" : s;
    }

    private String fallback(String primary, String secondary) {
        if (primary != null && !primary.isBlank()) return primary;
        if (secondary != null && !secondary.isBlank()) return secondary;
        return "暂无";
    }

    /**
     * 类型安全的失败响应 helper
     */
    private static <T> JsonResult<T> failedResult(ResponseCode code, String message) {
        JsonResult<T> result = new JsonResult<>();
        result.setState(code.getValue());
        result.setMessage(message);
        return result;
    }
}

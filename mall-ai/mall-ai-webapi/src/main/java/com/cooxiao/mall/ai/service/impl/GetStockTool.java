package com.cooxiao.mall.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.cooxiao.mall.pojo.product.vo.SkuStandardVO;
import com.cooxiao.mall.product.service.front.IForFrontSkuService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存查询工具（TODO #32-P1，{@code get_stock}）—— <b>走真实 Dubbo 到商品库</b>，不是 ES。
 *
 * <p>与 {@link SearchProductsTool} 形成互补：ES 适合"模糊找商品"，而"还有货吗/我的规格有货吗"
 * 是**强一致的事务数据**，必须问源库（ES 索引是异步同步的，库存字段不能当准）。
 *
 * <p>⚠️ 只覆盖**常规库存**（{@code pms_sku.stock}），不含秒杀场次库存（那在 mall-seckill 的表里）——
 * 这一点必须写在工具说明与 observation 里，否则模型会把"没货"误答成"永远买不到"。
 */
@Slf4j
@Component
public class GetStockTool implements AiTool {

    /** observation 里最多列几个 SKU：够模型判断，又不至于把上下文撑爆 */
    private static final int MAX_SKUS = 10;

    @DubboReference
    private IForFrontSkuService skuService;

    @Override
    public String name() {
        return "get_stock";
    }

    @Override
    public String description() {
        return "查询某个商品（spuId）的真实库存：返回该商品的总库存与各规格 SKU 的库存、价格。"
                + "当用户问「还有货吗」「我要的规格有没有货」「什么时候能买」时调用它。"
                + "spuId 从 search_products 或 compare_products 的结果里取（商品项自带 spuId）。"
                + "注意：只包含常规库存，不含秒杀活动库存。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spuId", Map.of("type", "integer",
                "description", "商品 SPU ID（从 search_products / compare_products 的结果中获取）"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("spuId"));
        return schema;
    }

    @Override
    public AiToolResult execute(Map<String, Object> args) {
        Object raw = args == null ? null : args.get("spuId");
        Long spuId = longOf(raw);
        if (spuId == null || spuId <= 0) {
            return AiToolResult.error("参数 spuId 非法（必须是正整数，且应从 search_products 的结果里取到）");
        }

        try {
            List<SkuStandardVO> skus = skuService.getSkusBySpuId(spuId);
            List<Map<String, Object>> items = new ArrayList<>();
            int totalStock = 0;
            if (skus != null) {
                for (SkuStandardVO sku : skus) {
                    int stock = sku.getStock() == null ? 0 : sku.getStock();
                    totalStock += stock;
                    if (items.size() < MAX_SKUS) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("skuId", sku.getId());
                        item.put("spec", sku.getSpecifications() == null ? sku.getTitle() : sku.getSpecifications());
                        item.put("price", sku.getPrice());
                        item.put("stock", stock);
                        items.add(item);
                    }
                }
            }

            Map<String, Object> observation = new LinkedHashMap<>();
            observation.put("spuId", spuId);
            observation.put("skuCount", skus == null ? 0 : skus.size());
            observation.put("totalStock", totalStock);
            observation.put("note", "常规库存（不含秒杀活动库存）");
            if (items.isEmpty()) {
                observation.put("hint", "该商品没有可售 SKU（可能未发布或已下架），请如实告知用户");
            } else if (skus != null && skus.size() > items.size()) {
                observation.put("hint", "SKU 较多，这里只列了前 " + items.size() + " 个");
            }
            observation.put("skus", items);

            log.info("工具 get_stock：spuId={} → {} 个 SKU，总库存 {}", spuId, skus == null ? 0 : skus.size(), totalStock);
            // 库存工具不产出商品卡片（不返回 hits），前端商品列表由检索/对比类工具负责
            return AiToolResult.text(JSON.toJSONString(observation));
        } catch (Exception e) {
            log.warn("工具 get_stock 调用失败：spuId={}, {}", spuId, e.getMessage());
            return AiToolResult.error("库存服务暂时不可用，请稍后再试");
        }
    }

    /** 模型可能把 id 写成数字（Integer/Long/BigDecimal）或数字字符串，统一收敛 */
    private static Long longOf(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

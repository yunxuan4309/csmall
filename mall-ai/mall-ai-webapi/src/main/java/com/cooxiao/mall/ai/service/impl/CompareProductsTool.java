package com.cooxiao.mall.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.cooxiao.mall.pojo.product.vo.SpuStandardVO;
import com.cooxiao.mall.product.service.front.IForFrontSpuService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品对比工具（TODO #32-P1，{@code compare_products}）。
 *
 * <p><b>为什么不用现成的 {@code ProductCompareServiceImpl.compare(...)}</b>：那个方法内部会**再调一次 LLM**
 * 生成对比总结。工具里套 LLM 会带来三个问题：① 一次工具调用变成两次计费；② Agent 循环本来就持有并发闸门，
 * 嵌套调用会多占一个槽；③ **工具应该只提供事实，"判断"留给 Agent 的收敛轮**（这与我们在 P0 观察到的
 * "模型会自己剔除误召回、会如实说没有"是同一套设计哲学）。所以这里只查库、只摆事实。
 */
@Slf4j
@Component
public class CompareProductsTool implements AiTool {

    private static final int MIN_IDS = 2;
    private static final int MAX_IDS = 3;

    private static final List<String> DIMENSIONS = List.of(
            "价格", "品牌", "分类", "销量", "库存", "标签", "标题");

    @DubboReference
    private IForFrontSpuService spuService;

    @Override
    public String name() {
        return "compare_products";
    }

    @Override
    public String description() {
        return "把 2~3 个**指定商品**（按 spuId）并排比较，返回可横向对比的事实：价格 / 品牌 / 分类 / 销量 / 库存 / 标签 / 标题。"
                + "当用户要求「A 和 B 哪个好」「帮我比一比」时调用；也可以把 search_products 返回的候选拿来做并排比较。"
                + "⚠️ 需要 2~3 个不同的 spuId；找商品请用 search_products，不要用它做检索。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> ids = Map.of(
                "type", "array",
                "items", Map.of("type", "integer"),
                "description", "要对比的商品 SPU ID 列表（2~3 个，从 search_products 的结果里取）");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spuIds", ids);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("spuIds"));
        return schema;
    }

    @Override
    public AiToolResult execute(Map<String, Object> args) {
        // ---- ① 参数收敛：去重、丢非法值、封顶数量 ----
        Set<Long> ids = new LinkedHashSet<>();
        Object raw = args == null ? null : args.get("spuIds");
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                Long id = longOf(item);
                if (id != null && id > 0) {
                    ids.add(id);
                }
            }
        } else {
            Long single = longOf(raw);
            if (single != null && single > 0) {
                ids.add(single);
            }
        }
        if (ids.size() < MIN_IDS) {
            return AiToolResult.error("compare_products 需要 " + MIN_IDS + "~" + MAX_IDS
                    + " 个不同的 spuId（当前有效值 " + ids.size() + " 个），请先从 search_products 里取到商品再对比");
        }
        List<Long> targets = new ArrayList<>(ids).subList(0, Math.min(ids.size(), MAX_IDS));

        // ---- ② 查库（每个 SPU 一次 Dubbo 调用；失败/不存在都不打断，如实记录在观察结果里）----
        List<Map<String, Object>> products = new ArrayList<>();
        List<Long> missing = new ArrayList<>();
        List<Map<String, Object>> docs = new ArrayList<>();
        for (Long id : targets) {
            try {
                SpuStandardVO spu = spuService.getSpuById(id);
                if (spu == null) {
                    missing.add(id);
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("spuId", spu.getId());
                item.put("name", spu.getName());
                item.put("price", spu.getListPrice());
                item.put("brand", spu.getBrandName());
                item.put("category", spu.getCategoryName());
                item.put("sales", spu.getSales());
                item.put("stock", spu.getStock());
                item.put("tags", spu.getTags());
                item.put("title", spu.getTitle());
                products.add(item);
                docs.add(toDoc(spu));
            } catch (Exception e) {
                log.warn("工具 compare_products 查询失败：spuId={}, {}", id, e.getMessage());
                missing.add(id);
            }
        }

        if (products.isEmpty()) {
            return AiToolResult.error("这些 spuId 都查不到商品，请确认 spuId 是否来自 search_products 的结果");
        }

        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("dimensions", DIMENSIONS);
        observation.put("count", products.size());
        if (!missing.isEmpty()) {
            observation.put("missingSpuIds", missing);
        }
        observation.put("products", products);

        log.info("工具 compare_products：请求 {} 个，取到 {} 个（缺失 {}）", targets.size(), products.size(), missing.size());
        return new AiToolResult(JSON.toJSONString(observation), docs);
    }

    /**
     * SPU → 前端商品卡片所需的字段形状（与 ES 文档对齐）。
     * <p>这样"对比"也能在前端直接出商品卡片，而不必为它单独写一套渲染逻辑。
     */
    private Map<String, Object> toDoc(SpuStandardVO spu) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("spuId", spu.getId());
        doc.put("name", spu.getName());
        doc.put("title", spu.getTitle());
        doc.put("brandName", spu.getBrandName());
        doc.put("categoryName", spu.getCategoryName());
        doc.put("listPrice", spu.getListPrice());
        doc.put("sales", spu.getSales());
        doc.put("tags", spu.getTags());
        doc.put("pictures", spu.getPictures());
        return doc;
    }

    /** 模型可能把 id 写成数字或数字字符串，统一收敛 */
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

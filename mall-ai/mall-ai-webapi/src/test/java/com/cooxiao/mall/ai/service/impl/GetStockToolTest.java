package com.cooxiao.mall.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cooxiao.mall.pojo.product.vo.SkuStandardVO;
import com.cooxiao.mall.pojo.product.vo.SpuStandardVO;
import com.cooxiao.mall.product.service.front.IForFrontSkuService;
import com.cooxiao.mall.product.service.front.IForFrontSpuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 库存工具（TODO #32-P1）—— 重点验证"模型参数不可信"与"依赖故障不炸链路"。
 *
 * <p>⚠️ 这里用 {@link java.lang.reflect.Proxy} 造假 Dubbo 服务，而不是手写实现类：
 * {@code IForFrontSkuService} 现在只有一个方法，但同类接口（如 {@code IForFrontSpuService}）有 5 个 ——
 * 用代理只关注"被测方法"，接口新增方法时测试不会莫名其妙编译不过。
 */
class GetStockToolTest {

    private boolean providerDown;
    private List<SkuStandardVO> skus;
    private final List<Long> calledWith = new ArrayList<>();
    private final Map<Long, SpuStandardVO> spus = new LinkedHashMap<>();
    private GetStockTool tool;

    @BeforeEach
    void setUp() {
        skus = new ArrayList<>();
        providerDown = false;
        calledWith.clear();
        spus.clear();
        spus.put(2L, spu(2L, "酷鲨手机", 4999));

        IForFrontSkuService fakeSku = (IForFrontSkuService) Proxy.newProxyInstance(
                IForFrontSkuService.class.getClassLoader(),
                new Class<?>[]{IForFrontSkuService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getSkusBySpuId":
                            calledWith.add((Long) args[0]);
                            if (providerDown) {
                                throw new IllegalStateException("provider down");
                            }
                            return skus;
                        case "toString":
                            return "fakeSkuService";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            return null;
                    }
                });

        // 库存工具会先查 SPU（为的是把商品名带进 observation，避免张冠李戴）
        IForFrontSpuService fakeSpu = (IForFrontSpuService) Proxy.newProxyInstance(
                IForFrontSpuService.class.getClassLoader(),
                new Class<?>[]{IForFrontSpuService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getSpuById":
                            return spus.get((Long) args[0]);
                        case "toString":
                            return "fakeSpuService";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            return null;
                    }
                });

        tool = new GetStockTool();
        ReflectionTestUtils.setField(tool, "skuService", fakeSku);
        ReflectionTestUtils.setField(tool, "spuService", fakeSpu);
    }

    private static SpuStandardVO spu(long id, String name, double price) {
        SpuStandardVO vo = new SpuStandardVO();
        vo.setId(id);
        vo.setName(name);
        vo.setListPrice(BigDecimal.valueOf(price));
        return vo;
    }

    private static SkuStandardVO sku(long id, String spec, double price, int stock) {
        SkuStandardVO vo = new SkuStandardVO();
        vo.setId(id);
        vo.setSpecifications(spec);
        vo.setPrice(BigDecimal.valueOf(price));
        vo.setStock(stock);
        return vo;
    }

    @Test
    void schema_requiresSpuId() {
        Map<String, Object> schema = tool.parameters();

        assertThat(tool.name()).isEqualTo("get_stock");
        assertThat(tool.description()).contains("常规库存");   // 必须提醒模型：不含秒杀库存
        assertThat(schema.get("required")).isEqualTo(List.of("spuId"));
    }

    @Test
    void aggregatesTotalStock_andListsSkuDetails() {
        skus.add(sku(11, "8+256G", 6999, 10));
        skus.add(sku(12, "12+512G", 7999, 5));

        AiToolResult result = tool.execute(Map.of("spuId", 2));

        JSONObject observation = JSON.parseObject(result.observation());
        assertThat(observation.getString("spuName")).isEqualTo("酷鲨手机");   // ★ 自报家门，防张冠李戴
        assertThat(observation.getIntValue("skuCount")).isEqualTo(2);
        assertThat(observation.getIntValue("totalStock")).isEqualTo(15);
        JSONArray items = observation.getJSONArray("skus");
        assertThat(items).hasSize(2);
        assertThat(items.getJSONObject(0).getString("spec")).isEqualTo("8+256G");
        assertThat(items.getJSONObject(0).getIntValue("stock")).isEqualTo(10);
        assertThat(calledWith).containsExactly(2L);
        assertThat(result.hits()).isEmpty();   // 库存工具不产出前端商品卡片
    }

    @Test
    void unknownSpu_returnsReadableError_insteadOfQueryingSkus() {
        AiToolResult result = tool.execute(Map.of("spuId", 999));

        assertThat(result.observation()).contains("查不到商品");
        assertThat(calledWith).isEmpty();     // SPU 不存在就不必再查 SKU
    }

    @Test
    void acceptsNumericStringId() {
        skus.add(sku(11, "标准版", 1999, 3));

        tool.execute(Map.of("spuId", "2"));

        assertThat(calledWith).containsExactly(2L);
    }

    @Test
    void invalidSpuId_returnsReadableError_insteadOfThrowing() {
        AiToolResult missing = tool.execute(Map.of());
        AiToolResult nonNumeric = tool.execute(Map.of("spuId", "abc"));
        AiToolResult negative = tool.execute(Map.of("spuId", -5));

        assertThat(missing.observation()).contains("spuId");
        assertThat(nonNumeric.observation()).contains("spuId");
        assertThat(negative.observation()).contains("spuId");
        assertThat(calledWith).isEmpty();     // 非法参数绝不落到下游
    }

    @Test
    void providerDown_returnsReadableError_notException() {
        providerDown = true;

        AiToolResult result = tool.execute(Map.of("spuId", 2));

        assertThat(result.observation()).contains("库存服务");
        assertThat(result.hits()).isEmpty();
    }

    @Test
    void noSkus_hintsThatProductMayBeUnavailable() {
        AiToolResult result = tool.execute(Map.of("spuId", 2));

        JSONObject observation = JSON.parseObject(result.observation());
        assertThat(observation.getIntValue("skuCount")).isZero();
        assertThat(observation.getIntValue("totalStock")).isZero();
        assertThat(observation.getString("hint")).contains("没有可售 SKU");
    }

    @Test
    void capsSkuListAtTen_butTotalStockCountsAll() {
        for (int i = 1; i <= 12; i++) {
            skus.add(sku(i, "规格" + i, 100 + i, 1));
        }

        AiToolResult result = tool.execute(Map.of("spuId", 2));

        JSONObject observation = JSON.parseObject(result.observation());
        assertThat(observation.getJSONArray("skus")).hasSize(10);
        assertThat(observation.getIntValue("skuCount")).isEqualTo(12);
        assertThat(observation.getIntValue("totalStock")).isEqualTo(12);
        assertThat(observation.getString("hint")).contains("只列了前 10 个");
    }
}

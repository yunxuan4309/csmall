package com.cooxiao.mall.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cooxiao.mall.pojo.product.vo.SpuStandardVO;
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
 * 商品对比工具（TODO #32-P1）。
 *
 * <p>设计取舍值得记：**没有复用现成的 {@code ProductCompareServiceImpl.compare(...)}** —— 那个方法内部会再调一次 LLM
 * 生成对比总结，于是"一次工具调用"变成"两次 LLM 计费 + 多占一个并发闸门槽"，而且违反"工具只提供事实、
 * 判断留给 Agent 收敛轮"的原则。所以这里只查库、只摆事实。
 */
class CompareProductsToolTest {

    private final Map<Long, SpuStandardVO> spus = new LinkedHashMap<>();
    private final List<Long> calledWith = new ArrayList<>();
    private boolean providerDown;
    private CompareProductsTool tool;

    @BeforeEach
    void setUp() {
        spus.clear();
        calledWith.clear();
        providerDown = false;

        // 用动态代理：IForFrontSpuService 有 5 个方法，测试只关心 getSpuById
        IForFrontSpuService fake = (IForFrontSpuService) Proxy.newProxyInstance(
                IForFrontSpuService.class.getClassLoader(),
                new Class<?>[]{IForFrontSpuService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getSpuById":
                            calledWith.add((Long) args[0]);
                            if (providerDown) {
                                throw new IllegalStateException("provider down");
                            }
                            return spus.get(args[0]);
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

        tool = new CompareProductsTool();
        ReflectionTestUtils.setField(tool, "spuService", fake);
    }

    private void addSpu(long id, String name, double price, String brand, int sales, int stock) {
        SpuStandardVO spu = new SpuStandardVO();
        spu.setId(id);
        spu.setName(name);
        spu.setTitle(name + " 官方旗舰");
        spu.setListPrice(BigDecimal.valueOf(price));
        spu.setBrandName(brand);
        spu.setCategoryName("手机");
        spu.setSales(sales);
        spu.setStock(stock);
        spu.setTags("5G");
        spus.put(id, spu);
    }

    @Test
    void schema_requiresSpuIds() {
        Map<String, Object> schema = tool.parameters();

        assertThat(tool.name()).isEqualTo("compare_products");
        assertThat(schema.get("required")).isEqualTo(List.of("spuIds"));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("spuIds");
    }

    @Test
    void comparesTwoProducts_andReturnsFacts() {
        addSpu(2, "华为 Mate 60 Pro", 6999, "华为", 81, 30);
        addSpu(5, "小米 14 Pro", 4999, "小米", 120, 45);

        AiToolResult result = tool.execute(Map.of("spuIds", List.of(2, 5)));

        JSONObject observation = JSON.parseObject(result.observation());
        assertThat(observation.getIntValue("count")).isEqualTo(2);
        assertThat(observation.getJSONArray("dimensions")).contains("价格", "库存");
        JSONArray products = observation.getJSONArray("products");
        assertThat(products.getJSONObject(0).getString("brand")).isEqualTo("华为");
        assertThat(products.getJSONObject(1).getIntValue("stock")).isEqualTo(45);
        // 同时给前端商品卡片（hits 按 ES 文档字段形状组装）
        assertThat(result.hits()).hasSize(2);
        assertThat(result.hits().get(0)).containsEntry("name", "华为 Mate 60 Pro")
                .containsEntry("brandName", "华为")
                .containsEntry("spuId", 2L);
    }

    @Test
    void deduplicatesAndCapsToThree() {
        addSpu(1, "A", 100, "X", 1, 1);
        addSpu(2, "B", 200, "Y", 2, 2);
        addSpu(3, "C", 300, "Z", 3, 3);
        addSpu(4, "D", 400, "W", 4, 4);

        AiToolResult result = tool.execute(Map.of("spuIds", List.of(1, 1, 2, 3, 4)));

        JSONObject observation = JSON.parseObject(result.observation());
        assertThat(observation.getIntValue("count")).isEqualTo(3);   // 去重 + 封顶 3
        assertThat(calledWith).containsExactly(1L, 2L, 3L);
    }

    @Test
    void fewerThanTwoValidIds_isRejected() {
        addSpu(1, "A", 100, "X", 1, 1);

        AiToolResult result = tool.execute(Map.of("spuIds", List.of(1)));

        assertThat(result.observation()).contains("2~3");
        assertThat(calledWith).isEmpty();       // 参数不合法就不该打下游
    }

    @Test
    void invalidIdsAreDropped_andAcceptNumericStrings() {
        addSpu(2, "B", 200, "Y", 2, 2);
        addSpu(5, "E", 500, "V", 5, 5);

        AiToolResult result = tool.execute(Map.of("spuIds", List.of("2", "abc", -1, 5)));

        JSONObject observation = JSON.parseObject(result.observation());
        assertThat(observation.getIntValue("count")).isEqualTo(2);
        assertThat(calledWith).containsExactly(2L, 5L);
    }

    @Test
    void missingSpuIds_areReportedNotFatal() {
        addSpu(2, "B", 200, "Y", 2, 2);

        AiToolResult result = tool.execute(Map.of("spuIds", List.of(2, 999)));

        JSONObject observation = JSON.parseObject(result.observation());
        assertThat(observation.getIntValue("count")).isEqualTo(1);
        assertThat(observation.getJSONArray("missingSpuIds")).containsExactly(999);
    }

    @Test
    void allIdsMissing_returnsReadableError() {
        AiToolResult result = tool.execute(Map.of("spuIds", List.of(888, 999)));

        assertThat(result.observation()).contains("查不到商品");
        assertThat(result.hits()).isEmpty();
    }

    @Test
    void providerDown_returnsReadableError() {
        providerDown = true;

        AiToolResult result = tool.execute(Map.of("spuIds", List.of(2, 5)));

        assertThat(result.observation()).contains("查不到商品");
    }
}

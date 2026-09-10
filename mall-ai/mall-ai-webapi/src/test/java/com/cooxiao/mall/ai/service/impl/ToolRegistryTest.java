package com.cooxiao.mall.ai.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工具注册表（TODO #32 P0）。
 *
 * <p>重点验证两件"以后加工具时最容易踩"的事：
 * ① 注册表按名字索引，新增工具<b>不需要改注册表代码</b>（构造器注入自动收集）；
 * ② 同名工具必须<b>启动期就炸</b>——否则会变成"模型点了 A、执行的却是 B"的静默故障。
 */
class ToolRegistryTest {

    private static AiTool stub(String name) {
        return new AiTool() {
            @Override public String name() { return name; }
            @Override public String description() { return "stub:" + name; }
            @Override public Map<String, Object> parameters() { return Map.of("type", "object"); }
            @Override public AiToolResult execute(Map<String, Object> args) { return AiToolResult.text("ok"); }
        };
    }

    @Test
    void schemas_areOpenAiCompatible_andKeepRegistrationOrder() {
        ToolRegistry registry = new ToolRegistry(List.of(stub("search_products"), stub("get_product_detail")));

        List<Map<String, Object>> schemas = registry.toolSchemas();

        assertThat(registry.size()).isEqualTo(2);
        assertThat(schemas).hasSize(2);
        assertThat(schemas.get(0)).containsEntry("type", "function");
        @SuppressWarnings("unchecked")
        Map<String, Object> function = (Map<String, Object>) schemas.get(0).get("function");
        assertThat(function).containsEntry("name", "search_products").containsKey("parameters");
        assertThat(registry.find("get_product_detail")).isNotNull();
        assertThat(registry.find("not_exists")).isNull();
        assertThat(registry.find(null)).isNull();
    }

    @Test
    void duplicateToolName_failsFast() {
        assertThatThrownBy(() -> new ToolRegistry(List.of(stub("search_products"), stub("search_products"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("工具名重复");
    }
}

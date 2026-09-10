package com.cooxiao.mall.ai.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表（TODO #32 P0）。
 *
 * <p>所有 {@link AiTool} 实现都是 Spring Bean，这里按名字建索引，供两处使用：
 * ① 组请求体的 {@code tools} 数组（JSON Schema）；② 按模型返回的工具名找到实现去执行。
 *
 * <p>用<b>构造器注入 {@code List<AiTool>}</b> 而不是手工 new：以后新增工具只要写一个
 * {@code @Component} 就自动生效，<b>不需要改这个类</b>（这正是 P0 只放一个工具却先建注册表的原因）。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AiTool> tools;

    public ToolRegistry(List<AiTool> toolBeans) {
        Map<String, AiTool> map = new LinkedHashMap<>();
        for (AiTool tool : toolBeans) {
            AiTool previous = map.put(tool.name(), tool);
            if (previous != null) {
                // 同名工具会让"模型点了 A，执行的却是 B"，属于静默故障，必须炸在启动期
                throw new IllegalStateException("AI 工具名重复: " + tool.name()
                        + "（" + previous.getClass().getName() + " / " + tool.getClass().getName() + "）");
            }
        }
        this.tools = Collections.unmodifiableMap(map);
        log.info("已注册 AI 工具 {} 个: {}", tools.size(), tools.keySet());
    }

    /** 请求体用的 {@code tools} 数组（OpenAI 兼容格式） */
    public List<Map<String, Object>> toolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>(tools.size());
        for (AiTool tool : tools.values()) {
            schemas.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", tool.name(),
                            "description", tool.description(),
                            "parameters", tool.parameters())));
        }
        return schemas;
    }

    /** 按名字找工具；找不到返回 {@code null}（由调用方回灌一条"未知工具"的观察结果，不中断循环） */
    public AiTool find(String name) {
        return name == null ? null : tools.get(name);
    }

    /** 已注册工具数量（供启动自检/日志使用） */
    public int size() {
        return tools.size();
    }
}

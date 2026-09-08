package com.cooxiao.mall.search.service;

import com.cooxiao.mall.pojo.ai.vo.SearchResultVO;

/**
 * 搜索服务接口（TODO #33 方案 A2 改造后）
 *
 * <p>mall-search 从"自建 index2 索引 + 手动全量同步"改为 <b>只读统一索引</b>
 * （cool_shark_mall_ai）的普通搜索降级层：
 * <ul>
 *   <li>不再向 ES 写入任何数据（数据由 mall-ai 经 Dubbo 变更订阅维护，单写链路）</li>
 *   <li>本服务只做 <b>普通关键词召回</b>：纯 ES multi_match，无 AI 意图解析、无重排、零 LLM 依赖</li>
 *   <li>定位：进程级降级通道 —— mall-ai（AI 搜索）整体不可用时，前端 fallback 到本服务的 /search 仍可搜索</li>
 * </ul>
 */
public interface ISearchService {

    /**
     * 普通关键词搜索（只读统一索引 cool_shark_mall_ai）
     *
     * @param keyword  搜索关键词（空白时返回空结果，不做 matchAll 全量返回）
     * @param page     页码（从 1 开始，<=0 归一为 1）
     * @param pageSize 每页条数（<=0 归一为默认 10；>100 截断为 100）
     * @return 与 AI 搜索同构的 SearchResultVO（products/totalCount），前端 fallback 零适配
     */
    SearchResultVO search(String keyword, Integer page, Integer pageSize);
}

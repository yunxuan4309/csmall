package com.cooxiao.mall.search.controller;

import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.pojo.ai.vo.SearchResultVO;
import com.cooxiao.mall.search.service.ISearchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通搜索控制器（TODO #33 方案 A2 改造后）
 *
 * <p>mall-search 定位 = <b>只读统一索引的普通搜索降级层</b>：
 * <ul>
 *   <li>GET /search —— 普通关键词召回（纯 ES multi_match，零 AI/LLM 依赖，毫秒级）</li>
 *   <li>已移除：/search/sync（不再自建索引，无需手动全量同步）、/search/byLogstash（死代码）</li>
 *   <li>返回结构与 /ai/search 同构（SearchResultVO），前端 fallback 零适配</li>
 * </ul>
 */
@RestController
@RequestMapping("/search")
@Api(tags = "搜索模块（普通搜索降级层）")
public class SearchController {

    @Autowired
    private ISearchService searchService;

    @GetMapping()
    @ApiOperation("普通关键词搜索：只读统一索引，纯 ES 召回（AI 搜索的进程级降级通道）")
    @ApiImplicitParams({
            @ApiImplicitParam(value = "搜索关键词", name = "keyword", example = "手机"),
            @ApiImplicitParam(value = "页码", name = "page", example = "1"),
            @ApiImplicitParam(value = "每页条数", name = "pageSize", example = "10")
    })
    public JsonResult<SearchResultVO> searchByKeyword(
            String keyword, Integer page, Integer pageSize) {
        SearchResultVO result = searchService.search(keyword, page, pageSize);
        return JsonResult.ok(result);
    }

}

package com.cooxiao.mall.search.controller;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.pojo.search.entity.SpuEntity;
import com.cooxiao.mall.pojo.search.entity.SpuForElastic;
import com.cooxiao.mall.search.service.ISearchService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/26
 */
@RestController
@RequestMapping("/search")
@Api(tags = "搜索模块")
public class SearchController {
    //通过手写代码查询es
    @Autowired
    @Qualifier("searchServiceImpl")
    private ISearchService searchService;

    //通过logstash查询es数据
    @Autowired
    @Qualifier("searchRemoteServiceImpl")
    private ISearchService searchRemoteServiceImpl;

    //以下是查询将数据库的手动存入es中;
    // 当前方法注解@GetMapping后面什么都不写
    // 表示当前方法使用类上的路径做url
    // localhost:10008/search

    @GetMapping()
    @ApiOperation("根据用户输入的关键字分页查询商品信息:手动查询ES")
    @ApiImplicitParams({
            @ApiImplicitParam(value = "搜索关键字",name="keyword",example = "手机"),
            @ApiImplicitParam(value = "页码",name="page",example = "1"),
            @ApiImplicitParam(value = "每页条数",name="pageSize",example = "2")
    })
    public JsonResult<JsonPage<SpuForElastic>> searchByKeyword(
            String keyword,Integer page,Integer pageSize){
        JsonPage<SpuForElastic> jsonPage=
                searchService.search(keyword, page, pageSize);
        return JsonResult.ok(jsonPage);
    }

    //以下是查询数据中的数据,通过logStash存入es中
    @GetMapping("/byLogstash")
    @ApiOperation("根据用户输入的关键字分页查询商品信息:logstash查询ES")
    @ApiImplicitParams({
            @ApiImplicitParam(value = "搜索关键字",name="keyword",example = "手机"),
            @ApiImplicitParam(value = "页码",name="page",example = "1"),
            @ApiImplicitParam(value = "每页条数",name="pageSize",example = "2")
    })
    public JsonResult<JsonPage<SpuEntity>> searchByKeywordByLogStash(
            String keyword,Integer page,Integer pageSize){
        JsonPage<SpuEntity> jsonPage=
                searchRemoteServiceImpl.searchByLogStash(keyword, page, pageSize);
        return JsonResult.ok(jsonPage);
    }

}

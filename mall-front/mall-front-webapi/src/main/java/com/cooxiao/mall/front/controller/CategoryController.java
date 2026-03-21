package com.cooxiao.mall.front.controller;

import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.front.service.IFrontCategoryService;
import com.cooxiao.mall.pojo.front.entity.FrontCategoryEntity;
import com.cooxiao.mall.pojo.front.vo.FrontCategoryTreeVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/front/category")//前端路径已经写好,所以保持一致
@Api(tags = "前台分类查询")
public class CategoryController {
    //IForFrontCategoryService 是product提供给front的dubbo服务;
    //IFrontCategoryService 是front提供给front controller自己的service
    @Autowired
    private IFrontCategoryService categoryService;

    @GetMapping("/all")
    @ApiOperation("查询获取三级分类树对象")
    public JsonResult<FrontCategoryTreeVO<FrontCategoryEntity>> getTreeVO(){
        FrontCategoryTreeVO<FrontCategoryEntity> treeVO=categoryService.categoryTree();
        return JsonResult.ok(treeVO);
    }
}

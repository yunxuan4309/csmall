package com.cooxiao.mall.product.service.front;

import com.cooxiao.mall.pojo.product.vo.CategoryStandardVO;

import java.util.List;

/**
 * 为前台调用准备的接口类
 */
public interface IForFrontCategoryService {
    /**
     * 全量查询商品分类列表
     * @return
     */
    List<CategoryStandardVO> getCategoryList();
}

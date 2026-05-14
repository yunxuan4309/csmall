package com.cooxiao.mall.product.service;

import com.cooxiao.mall.pojo.product.vo.CategoryStandardVO;
import com.cooxiao.mall.product.service.front.IForFrontCategoryService;
import com.cooxiao.mall.product.mapper.CategoryMapper;
import com.cooxiao.mall.product.utils.ImageUrlPrefixHelper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@DubboService
@Service
public class ForFrontCategoryServiceImpl implements IForFrontCategoryService {
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private ImageUrlPrefixHelper imageUrlPrefixHelper;

    @Override
    public List<CategoryStandardVO> getCategoryList() {
        List<CategoryStandardVO> list = categoryMapper.selectAllCategories();
        for (CategoryStandardVO vo : list) {
            vo.setIcon(imageUrlPrefixHelper.processUrl(vo.getIcon()));
        }
        return list;
    }
}

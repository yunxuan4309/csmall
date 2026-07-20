package com.cooxiao.mall.front.service.test;

import com.cooxiao.mall.front.MallFrontWebApiApplication;
import com.cooxiao.mall.front.service.impl.FrontCategoryServiceImpl;
import com.cooxiao.mall.pojo.front.vo.FrontCategoryTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes= MallFrontWebApiApplication.class)
public class CategoryTest {
    @Autowired
    private FrontCategoryServiceImpl frontCategoryService;
    @Test
    public void test(){
        FrontCategoryTreeVO frontCategoryTreeVO = frontCategoryService.categoryTree();
        log.info("category tree: {}", frontCategoryTreeVO);
    }
}

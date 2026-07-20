package com.cooxiao.mall.search.test;

import com.cooxiao.mall.pojo.search.entity.SpuForElastic;
import com.cooxiao.mall.search.repository.SpuForElasticRepository;
import com.cooxiao.mall.search.service.ISearchService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
public class TestSearch {

//因为从数据库导入es数据库的流程中不需要反复运行,所以无需编写控制层,创建测试类运行即可
    @Autowired
    @Qualifier("searchServiceImpl")
    private ISearchService searchService;
    //加载数据到es
    @Test
    void loadData(){
        searchService.loadSpuByPage();
        log.info("数据加载完成");
    }

    @Autowired
    private SpuForElasticRepository spuRepository;
    @Test
    void showData(){
        Iterable<SpuForElastic> spus=spuRepository.findAll();
        spus.forEach(spu -> log.info("spu: {}", spu));
    }

    // 自定义查询
    @Test
    void queryTitle(){
        Iterable<SpuForElastic> spus=
                spuRepository.querySpuForElasticsByTitleMatches("手机");
        spus.forEach(spu -> log.info("queryTitle result: {}", spu));
    }

    @Test
    void querySearch(){
        Iterable<SpuForElastic> spus=spuRepository.querySearch("手机");
        spus.forEach(spu -> log.info("querySearch result: {}", spu));
    }

}

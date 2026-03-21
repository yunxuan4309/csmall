package com.cooxiao.mall.search.service;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.search.entity.SpuEntity;
import com.cooxiao.mall.pojo.search.entity.SpuForElastic;


public interface ISearchService {

    // ES分页查询spu的方法,数据库中
    JsonPage<SpuForElastic> search(String keyword, Integer page, Integer pageSize);
    JsonPage<SpuEntity> searchByLogStash(String keyword, Integer page, Integer pageSize);

    // 向ES中加载数据的方法
    void loadSpuByPage();
    void loadSpuByPageByLogStash();
}









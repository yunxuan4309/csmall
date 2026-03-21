package com.cooxiao.mall.search.service.impl;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.search.entity.SpuEntity;
import com.cooxiao.mall.pojo.search.entity.SpuForElastic;
import com.cooxiao.mall.search.repository.SpuEntityRepository;
import com.cooxiao.mall.search.service.ISearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/10/17
 * 通过logstash查询es 的数据;
 */

@Service
@Deprecated
@Slf4j
public class SearchRemoteServiceImpl implements ISearchService {

    // 装配新创建的ES持久层对象
    @Autowired
    private SpuEntityRepository spuEntityRepository;
    
    @Override
    public JsonPage<SpuForElastic> search(String keyword, Integer page, Integer pageSize) {
        return null;
    }
    @Override
    public void loadSpuByPage() {

    }
    //通过logstash查询es 的数据;
    //配套的工具,实时导入数据库的数据到ES中
    @Override
    public JsonPage<SpuEntity> searchByLogStash(String keyword, Integer page, Integer pageSize) {
        // 调用根据用户输入关键字执行分页查询Es的方法
        Page<SpuEntity> spus = spuEntityRepository.querySearchByText(
                keyword, PageRequest.of(page - 1, pageSize));
        // 将 Page类型的spus 转换为方法需要的返回值类型JsonPage
        JsonPage<SpuEntity> jsonPage=new JsonPage<>();
        jsonPage.setPage(page);
        jsonPage.setPageSize(pageSize);
        jsonPage.setTotalPage(spus.getTotalPages());
        jsonPage.setTotal(spus.getTotalElements());
        // 赋值分页数据
        jsonPage.setList(spus.getContent());
        // 最后别忘了返回
        return jsonPage;
    }



    @Override
    public void loadSpuByPageByLogStash() {

    }
}

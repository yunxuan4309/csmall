package com.cooxiao.mall.search.service.impl;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.product.model.Spu;
import com.cooxiao.mall.pojo.search.entity.SpuEntity;
import com.cooxiao.mall.pojo.search.entity.SpuForElastic;
import com.cooxiao.mall.product.service.front.IForFrontSpuService;
import com.cooxiao.mall.search.repository.SpuForElasticRepository;
import com.cooxiao.mall.search.service.ISearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/26
 * 手写代码查询es的数据
 */
//以下service将数据库的中数据存入ES中,现在通过logStash把数据库的数据转入es中,

@Service
@Slf4j
public class SearchServiceImpl implements ISearchService {
    // dubbo调用product模块分页查询所有spu信息的方法
    @DubboReference
    private IForFrontSpuService dubboSpuService;

    @Autowired
    private SpuForElasticRepository spuRepository;

    // ES 加载spu信息的方法,手写的
    @Override
    public void loadSpuByPage() {
        // 基本思路:dubbo查询数据库,返回spu的JsonPage,在批量新增到ES
        // 但是我们并不知道循环的次数,需要执行一次查询之后从返回的JsonPage中获取总页数,确定循环次数
        // 所以当前情况符合"先运行,后判断"的规则,所以建议使用do-while循环结构
        int i=1;   // 循环变量,从1开始,可以直接当做页码变量使用
        int pages; // 总页数的变量声明,第一次循环运行时会被赋值

        do{
            // dubbo调用从数据库查询一页数据
            // 实际开发的pageSize在500-1000之间,或者2000以内,这里写2只是为了让我们的数据能够有一页以上的效果
            JsonPage<Spu> spus=dubboSpuService.getSpuByPage(i,2);
            // 上面查询到的集合泛型类型是Spu,不是我们能执行新增到Es的泛型类型
            // 所以下面要先将这个集合中的对象转换为SpuForElastic类型
            List<SpuForElastic> esSpus=new ArrayList<>();
            // 遍历spus对象中的集合,将其中元素转换为SpuForElastic
            for(Spu spu : spus.getList()){
                SpuForElastic esSpu=new SpuForElastic();
                BeanUtils.copyProperties(spu,esSpu);
                // 将转换完成的esSpu添加到esSpus集合中
                esSpus.add(esSpu);
            }
            // esSpus集合中相当于本页数据,利用批量新增方法,将其新增到ES
            spuRepository.saveAll(esSpus);
            log.info("成功加载第{}页信息",i);
            // 为pages总页数变量赋值
            pages=spus.getTotalPage();
            // 循环变量自增
            i++;
        }while(i<=pages);

    }


    //手写代码查询es
    @Override
    public JsonPage<SpuForElastic> search(String keyword, Integer page, Integer pageSize) {
        // 分页条件中,page是1表示查询第一页,但是ES页码从0开始,所以要page-1
        Page<SpuForElastic> spus=spuRepository.querySearch(
                keyword, PageRequest.of(page-1,pageSize));
        // 查询完毕,需要将spus转换为JsonPage类型
        JsonPage<SpuForElastic> jsonPage=new JsonPage<>();
        // 分页信息赋值
        jsonPage.setPage(page);
        jsonPage.setPageSize(pageSize);
        jsonPage.setTotal(spus.getTotalElements());
        jsonPage.setTotalPage(spus.getTotalPages());
        // 分页数据赋值
        jsonPage.setList(spus.getContent());
        // 最后返回!!!!!!
        return jsonPage;
    }

    @Override
    public JsonPage<SpuEntity> searchByLogStash(String keyword, Integer page, Integer pageSize) {
        return null;
    }

    @Override
    public void loadSpuByPageByLogStash() {

    }
}

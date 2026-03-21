package com.cooxiao.mall.search.repository;

import com.cooxiao.mall.pojo.search.entity.SpuEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/10/17
 */
public interface SpuEntityRepository extends ElasticsearchRepository<SpuEntity, Long> {
    // 根据用户输入的关键字,搜索ES中匹配的商品
    // logstash将所有商品spu需要进行分词的字段,拼接成了一个新的字段search_text,实操
    // 又因为SpuEntity没有在类中声明这个字段,所以只能编写查询语句完成查询
    @Query("{\"match\":{\"search_text\":{\"query\":\"?0\"}}}")
    Page<SpuEntity> querySearchByText(String keyword, Pageable pageable);
}

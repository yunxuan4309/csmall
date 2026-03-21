package com.cooxiao.mall.search.repository;

import com.cooxiao.mall.pojo.search.entity.SpuForElastic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/26
 */

// SpuForElastic实体类操作ES的持久层接口
// 继承ElasticsearchRepository父接口后,能够直接使用SpuForElastic对ES进行批量新增操作
@Repository
public interface SpuForElasticRepository extends ElasticsearchRepository<SpuForElastic,Long> {
    // 自定义查询: title字段包含指定关键词(分词)的spu数据
    Iterable<SpuForElastic> querySpuForElasticsByTitleMatches(String title);//这里的参数与查的属性必须一样

    //
    /*
    * 第一个参数给?0,第二个参数给?1,第三个参数给?2,第四个参数给?3
    *     @Query("{\n" +
            "    \"bool\": {\n" +
            "      \"should\": [\n" +
            "        { \"match\": { \"name\":  \"?0\" }},\n" +
            "        { \"match\": { \"title\": \"?1\"}},\n" +
            "        { \"match\": { \"description\": \"?2\"}},\n" +
            "        { \"match\": { \"category_name\": \"?3\"}}\n" +
            "      ]\n" +
            "    }\n" +
            "  }")*/
    @Query("{\n" +
            "    \"bool\": {\n" +
            "      \"should\": [\n" +
            "        { \"match\": { \"name\":  \"?0\" }},\n" +
            "        { \"match\": { \"title\": \"?0\"}},\n" +
            "        { \"match\": { \"description\": \"?0\"}},\n" +
            "        { \"match\": { \"category_name\": \"?0\"}}\n" +
            "      ]\n" +
            "    }\n" +
            "  }")
//    @Query("{\n" +
//            "    \"bool\": {\n" +
//            "      \"should\": [\n" +
//            "        { \"match\": { \"name\":  \"?0\" }},\n" +
//            "        { \"match\": { \"title\": \"?0\"}},\n" +
//            "        { \"match\": { \"description\": \"?0\"}},\n" +
//            "        { \"match\": { \"category_name\": \"?0\"}}\n" +
//            "      ]\n" +
//            "    }\n" +
//            "  }")
        // 上面指定了查询语句,我们的方法名就可以脱离严格的规范(如querySpuForElasticsByTitleMatches),来任意起名了(见名知意即可)
// 当运行这个方法时,就会运行上面的查询语句,语句中?0的位置就是参数的占位符
// 因为我们查询的4个字段都是同一个分词,都写?0,所以只需要一个参数
    Iterable<SpuForElastic> querySearch(String keyword);

//实际查询的结果是要分页的,以下是分页的
    @Query("{\n" +
            "    \"bool\": {\n" +
            "      \"should\": [\n" +
            "        { \"match\": { \"name\":  \"?0\" }},\n" +
            "        { \"match\": { \"title\": \"?0\"}},\n" +
            "        { \"match\": { \"description\": \"?0\"}},\n" +
            "        { \"match\": { \"category_name\": \"?0\"}}\n" +
            "      ]\n" +
            "    }\n" +
            "  }")
// 上面指定了查询语句,我们的方法名就可以脱离严格的规范,来任意起名了(见名知意即可)
// 当运行这个方法时,就会运行上面的查询语句,语句中?0的位置就是参数的占位符
// 因为我们查询的4个字段都是同一个分词,都写?0,所以只需要一个参数
//↓↓↓                                           ↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓↓
    Page<SpuForElastic> querySearch(String keyword, Pageable pageable);


}

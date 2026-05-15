package com.cooxiao.mall.pojo.ai.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * ES 商品向量索引实体
 * 索引名称：cool_shark_mall_ai（通过 EsIndexInitializer 手动创建）
 */
@Data
@Document(indexName = "cool_shark_mall_ai", createIndex = false)
public class ProductAiEntity implements Serializable {

    @Id
    private Long spuId;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String name;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String description;

    @Field(type = FieldType.Keyword)
    private String categoryName;

    @Field(type = FieldType.Keyword)
    private String brandName;

    @Field(type = FieldType.Double)
    private BigDecimal listPrice;

    @Field(type = FieldType.Keyword)
    private String pictures;

    @Field(type = FieldType.Keyword)
    private String tags;

    @Field(type = FieldType.Integer)
    private Integer sales;

    /** 用于 RAG 检索的语义拼接文本（不含向量字段，向量通过 RestClient 写入） */
    private String semanticText;
}

package com.cooxiao.mall.ai.init;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.cooxiao.mall.ai.config.AiProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.StringReader;

/**
 * ES 索引自动初始化
 * 启动时检查 cool_shark_mall_ai 索引是否存在，不存在则创建含 dense_vector 映射的索引
 */
@Slf4j
@Component
public class EsIndexInitializer {

    private static final String INDEX_NAME = "cool_shark_mall_ai";

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private AiProperties aiProperties;

    @PostConstruct
    public void init() {
        try {
            boolean exists = esClient.indices().exists(
                    ExistsRequest.of(r -> r.index(INDEX_NAME))
            ).value();

            if (!exists) {
                createIndex();
                log.info("ES索引 [{}] 创建成功", INDEX_NAME);
            } else {
                log.info("ES索引 [{}] 已存在", INDEX_NAME);
            }
        } catch (Exception e) {
            log.warn("ES索引初始化失败（ES可能未启动），RAG功能将不可用：{}", e.getMessage());
        }
    }

    private void createIndex() throws Exception {
        String mapping = """
                {
                  "settings": {
                    "number_of_shards": 1,
                    "number_of_replicas": 0
                  },
                  "mappings": {
                    "dynamic": false,
                    "properties": {
                      "spuId":        { "type": "long" },
                      "name":         { "type": "text", "analyzer": "ik_max_word" },
                      "title":        { "type": "text", "analyzer": "ik_max_word" },
                      "description":  { "type": "text", "analyzer": "ik_max_word" },
                      "categoryName": { "type": "keyword" },
                      "brandName":    { "type": "keyword" },
                      "listPrice":    { "type": "double" },
                      "pictures":     { "type": "keyword" },
                      "tags":         { "type": "keyword" },
                      "sales":        { "type": "integer" },
                      "semanticText": { "type": "text", "analyzer": "ik_max_word" },
                      "semanticVector": {
                        "type": "dense_vector",
                        "dims": %d,
                        "index": true,
                        "similarity": "cosine"
                      }
                    }
                  }
                }
                """.formatted(aiProperties.getEmbeddingDimensions());

        esClient.indices().create(
                CreateIndexRequest.of(r -> r.index(INDEX_NAME).withJson(new StringReader(mapping)))
        );
    }
}

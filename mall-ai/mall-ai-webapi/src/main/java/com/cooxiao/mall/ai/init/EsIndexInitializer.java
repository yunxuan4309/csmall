package com.cooxiao.mall.ai.init;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.cooxiao.mall.ai.config.AiProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.io.StringReader;

/**
 * ES 索引自动初始化
 * 启动时检查 cool_shark_mall_ai 索引是否存在，不存在则创建含 dense_vector 映射的索引
 *
 * <p>⚠️ <b>只在索引不存在时创建</b> —— 所以一旦线上索引的 mapping 与这里不一致（曾经真实发生过，见 TODO #63：
 * 线上是 dynamic mapping、无 semanticVector、分词器也不是 IK），它<b>永远不会自愈</b>，
 * 只能"删索引 → 重启"重建。核对方法见 {@code docs/评估报告/商品与秒杀扩容方案.md} §一。
 *
 * <p>P0（2026-09-11）：{@code @DependsOn("embeddingSelfCheck")} 让 {@link com.cooxiao.mall.ai.config.EmbeddingSelfCheck}
 * <b>先跑</b> —— 避免"维度配置错了却先把索引按错误维度建出来"。
 * ⚠️ {@code @DependsOn} 只接受 Bean **名字符串**，与类名 `EmbeddingSelfCheck` 隐式耦合：
 * 若将来重命名该类，这里必须同步改（改错会**启动即报** "No bean named 'embeddingSelfCheck'"，不会静默）。
 */
@Slf4j
@Component
@DependsOn("embeddingSelfCheck")
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
                      },
                      "suggestField": {
                        "type": "completion",
                        "analyzer": "ik_max_word",
                        "preserve_separators": true,
                        "preserve_position_increments": true,
                        "max_input_length": 50
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

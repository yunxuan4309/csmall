package com.cooxiao.mall.ai.model;

import lombok.Data;

/**
 * AI 提取的搜索意图结构化参数
 */
@Data
public class SearchIntent {
    private Double budgetMin;
    private Double budgetMax;
    private String brand;
    private String category;
    private String keywords;
    /** relevance / sales / price_asc / price_desc */
    private String sortBy;
}

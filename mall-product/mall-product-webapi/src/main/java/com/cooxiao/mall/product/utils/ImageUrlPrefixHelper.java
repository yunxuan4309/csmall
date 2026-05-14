package com.cooxiao.mall.product.utils;

import com.alibaba.fastjson.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 图片 URL 前缀拼接工具。
 * 数据库中只存相对路径（如 spu_1_1.jpg），
 * 此工具根据当前环境的 resource-host 配置自动拼接完整 URL。
 */
@Slf4j
@Component
public class ImageUrlPrefixHelper {

    @Value("${custom.file-upload.resource-host}")
    private String resourceHost;

    /**
     * 处理 SPU/SKU 的 pictures 字段（JSON 数组字符串）。
     * 对数组中每个元素，如果是相对路径则拼接 resource-host 前缀。
     */
    public String processPictures(String pictures) {
        if (pictures == null || pictures.isEmpty()) {
            return pictures;
        }
        try {
            JSONArray arr = JSONArray.parseArray(pictures);
            boolean changed = false;
            for (int i = 0; i < arr.size(); i++) {
                String url = arr.getString(i);
                if (url != null && !url.startsWith("http://") && !url.startsWith("https://")) {
                    arr.set(i, resourceHost + url);
                    changed = true;
                }
            }
            return changed ? arr.toJSONString() : pictures;
        } catch (Exception e) {
            log.warn("处理图片URL失败，保留原始值: {}", pictures, e);
            return pictures;
        }
    }

    /**
     * 处理单个 URL 字段（如品牌 logo、分类 icon）。
     * 如果是相对路径则拼接 resource-host 前缀。
     */
    public String processUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        return resourceHost + url;
    }
}

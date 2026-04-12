package com.cooxiao.mall.pojo.product.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>SPU详情</p>
 *
 * @author cooxiao.com
 * @since 2021-11-30
 */
@Data
@TableName("pms_spu_detail")
public class SpuDetail implements Serializable {

    /**
     * 记录id
     */
    private Long id;

    /**
     * SPU id
     */
    private Long spuId;

    /**
     * SPU详情，应该使用HTML富文本，通常内容是若干张图片
     */
    private String content;

    /**
     * 数据创建时间
     */
    private LocalDateTime gmtCreate;

    /**
     * 数据最后修改时间
     */
    private LocalDateTime gmtModified;

}
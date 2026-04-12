package com.cooxiao.mall.pojo.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>品牌</p>
 *
 * @author cooxiao.com
 * @since 2021-11-30
 */

@Data
@TableName("pms_brand")
public class Brand implements Serializable {

    /**
     * 记录id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 品牌名称
     */
    private String name;

    /**
     * 品牌名称的拼音
     */
    private String pinyin;

    /**
     * 品牌logo的URL
     */
    private String logo;

    /**
     * 品牌简介
     */
    private String description;

    /**
     * 关键词列表，各关键词使用英文的逗号分隔
     */
    private String keywords;

    /**
     * 自定义排序序号
     */
    private Integer sort;

    /**
     * 销量（冗余）
     */
    private Integer sales;

    /**
     * 商品种类数量总和（冗余）
     */
    private Integer productCount;

    /**
     * 买家评论数量总和（冗余）
     */
    private Integer commentCount;

    /**
     * 买家好评数量总和（冗余）
     */
    private Integer positiveCommentCount;

    /**
     * 是否启用，1=启用，0=未启用
     */
    private Integer enable;

    /**
     * 数据创建时间
     */
    private LocalDateTime gmtCreate;

    /**
     * 数据最后修改时间
     */
    private LocalDateTime gmtModified;

}

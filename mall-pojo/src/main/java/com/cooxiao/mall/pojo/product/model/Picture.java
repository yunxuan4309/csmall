package com.cooxiao.mall.pojo.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>图片</p>
 *
 * @author cooxiao.com
 * @since 2021-11-30
 */
@Data
@TableName("pms_picture")
public class Picture implements Serializable {

    /**
     * 记录id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 相册id
     */
    private Long albumId;

    /**
     * 图片url
     */
    private String url;

    /**
     * 是否为封面图片，1=是，0=否
     */
    private Integer cover;

    /**
     * 图片简介
     */
    private String description;

    /**
     * 图片宽度，单位：px
     */
    private Integer width;

    /**
     * 图片高度，单位：px
     */
    private Integer height;

    /**
     * 自定义排序序号
     */
    private Integer sort;

    /**
     * 数据创建时间
     */
    private LocalDateTime gmtCreate;

    /**
     * 数据最后修改时间
     */
    private LocalDateTime gmtModified;

}
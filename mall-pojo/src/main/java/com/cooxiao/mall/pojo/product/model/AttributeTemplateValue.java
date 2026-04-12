package com.cooxiao.mall.pojo.product.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>模板的属性与值</p>
 *
 * @author cooxiao.com
 * @since 2021-11-30
 */
@Data
@TableName("pms_attribute_template_value")
public class AttributeTemplateValue implements Serializable {

    /**
     * 记录id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 模板id
     */
    private Long templateId;

    /**
     * 属性id
     */
    private Long attributeId;

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
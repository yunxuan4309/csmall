package com.cooxiao.mall.pojo.admin.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 权限表
 * </p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
@Data
@TableName("ams_permission")
public class Permission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 名称
     */

    private String name;

    /**
     * 值
     */

    private String value;

    /**
     * 描述
     */

    private String description;

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

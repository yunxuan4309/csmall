package com.cooxiao.mall.pojo.admin.model;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;


import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * ams_role_permission
 * </p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
@Data
@TableName("ams_role_permission")
public class RolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 角色id
     */

    private Long roleId;

    /**
     * 权限id
     */

    private Long permissionId;

    /**
     * 数据创建时间
     */

    private LocalDateTime gmtCreate;

    /**
     * 数据最后修改时间
     */

    private LocalDateTime gmtModified;


}

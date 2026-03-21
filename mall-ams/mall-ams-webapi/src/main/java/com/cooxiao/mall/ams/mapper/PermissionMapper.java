package com.cooxiao.mall.ams.mapper;

import com.cooxiao.mall.pojo.admin.model.Permission;
import com.cooxiao.mall.pojo.admin.query.PermissionQuery;
import com.cooxiao.mall.pojo.admin.vo.PermissionVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p> 权限表 Mapper 接口</p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
public interface PermissionMapper{
    List<Permission> selectPermissionsByAdminId(@Param("adminId")Long id);

    void insertPermission(Permission permission);

    List<PermissionVO> selectPermissions(PermissionQuery permissionQuery);

    void updatePermission(Permission permission);

    void deletePermission(@Param("id")Long id);
}

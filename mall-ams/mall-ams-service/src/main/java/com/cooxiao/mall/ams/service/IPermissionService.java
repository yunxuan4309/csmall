package com.cooxiao.mall.ams.service;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.admin.dto.PermissionAddDTO;
import com.cooxiao.mall.pojo.admin.dto.PermissionUpdateDTO;
import com.cooxiao.mall.pojo.admin.query.PermissionQuery;


/**
 * <p>
 * 权限表 服务类
 * </p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
public interface IPermissionService{

    void addPermission(PermissionAddDTO permissionAddDTO);

    JsonPage listPermissions(PermissionQuery permissionQuery);

    void updatePermission(PermissionUpdateDTO permissionUpdateDTO);

    void deletePermission(Long id);
}

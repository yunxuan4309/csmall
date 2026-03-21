package com.cooxiao.mall.ams.mapper;

import com.cooxiao.mall.pojo.admin.model.Role;
import com.cooxiao.mall.pojo.admin.vo.RoleVO;

import java.util.List;

/**
 * <p> 角色表 Mapper 接口</p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
public interface RoleMapper{
    int selectExistRoleById(Long roleId);

    List<RoleVO> selectRoles();

    List<RoleVO> selectRolesLikeName(String query);

    void insertRole(Role role);

    void updateRole(Role role);

    void deleteRol(Long id);
}

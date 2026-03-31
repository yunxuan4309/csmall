package com.cooxiao.mall.ams.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cooxiao.mall.pojo.admin.model.Role;

/**
 * <p> 角色表 Mapper 接口</p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
public interface RoleMapper extends BaseMapper<Role> {
    int selectExistRoleById(Long roleId);

    void insertRole(Role role);

    void updateRole(Role role);

    void deleteRol(Long id);
}

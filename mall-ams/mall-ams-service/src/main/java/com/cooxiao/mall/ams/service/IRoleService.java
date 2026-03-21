package com.cooxiao.mall.ams.service;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.admin.dto.RoleAddDTO;
import com.cooxiao.mall.pojo.admin.dto.RoleUpdateDTO;
import com.cooxiao.mall.pojo.admin.vo.RoleVO;

/**
 * <p>
 * 角色表 服务类
 * </p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
public interface IRoleService {

    JsonPage<RoleVO> listRoles(Integer pageNum, Integer sizeNum);

    JsonPage<RoleVO> queryRoles(Integer pageNum, Integer sizeNum, String query);

    void addRole(RoleAddDTO roleAddDTO);

    void updateRole(RoleUpdateDTO roleUpdateDTO);

    void deleteRole(Long id);
}

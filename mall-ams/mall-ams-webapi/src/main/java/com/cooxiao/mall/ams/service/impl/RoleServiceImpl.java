package com.cooxiao.mall.ams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cooxiao.mall.ams.exception.CoolSharkException;
import com.cooxiao.mall.ams.mapper.AdminRoleMapper;
import com.cooxiao.mall.ams.mapper.RoleMapper;
import com.cooxiao.mall.ams.service.IRoleService;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.admin.dto.RoleAddDTO;
import com.cooxiao.mall.pojo.admin.dto.RoleUpdateDTO;
import com.cooxiao.mall.pojo.admin.model.Role;
import com.cooxiao.mall.pojo.admin.vo.RoleVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 角色表 服务实现类
 * </p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
@Service
public class RoleServiceImpl implements IRoleService {
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private AdminRoleMapper adminRoleMapper;

    private RoleVO convertToVO(Role role) {
        if (role == null) {
            return null;
        }
        RoleVO vo = new RoleVO();
        BeanUtils.copyProperties(role, vo);
        return vo;
    }

    @Override
    public JsonPage<RoleVO> listRoles(Integer pageNum, Integer sizeNum) {
        Page<Role> page = new Page<>(pageNum, sizeNum);
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Role::getGmtCreate);
        Page<Role> result = roleMapper.selectPage(page, wrapper);
        List<RoleVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        IPage<RoleVO> pageVO = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageVO.setRecords(voList);
        return JsonPage.restPage(pageVO);
    }

    @Override
    public JsonPage<RoleVO> queryRoles(Integer pageNum, Integer sizeNum, String query) {
        Page<Role> page = new Page<>(pageNum, sizeNum);
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Role::getName, query)
               .or()
               .like(Role::getDescription, query)
               .orderByDesc(Role::getGmtCreate);
        Page<Role> result = roleMapper.selectPage(page, wrapper);
        List<RoleVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        IPage<RoleVO> pageVO = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageVO.setRecords(voList);
        return JsonPage.restPage(pageVO);
    }

    @Override
    public void addRole(RoleAddDTO roleAddDTO) {
        Role role=new Role();
        BeanUtils.copyProperties(roleAddDTO,role);
        roleMapper.insertRole(role);
    }

    @Override
    public void updateRole(RoleUpdateDTO roleUpdateDTO) {
        Role role=new Role();
        BeanUtils.copyProperties(roleUpdateDTO,role);
        roleMapper.updateRole(role);
    }

    @Override
    public void deleteRole(Long id) {
        int exist = roleMapper.selectExistRoleById(id);
        if(exist==0){
            throw new CoolSharkException("当前删除的角色不存在:"+id,409);
        }
        int count=adminRoleMapper.selectRelationByRoleid(id);
        if(count!=0){
            throw new CoolSharkException("当前角色关联了账号无法删除",409);
        }
        roleMapper.deleteRol(id);
    }
}

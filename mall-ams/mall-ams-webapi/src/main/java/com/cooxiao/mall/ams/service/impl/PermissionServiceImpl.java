package com.cooxiao.mall.ams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cooxiao.mall.ams.exception.CoolSharkException;
import com.cooxiao.mall.ams.mapper.RolePermissionMapper;
import com.cooxiao.mall.ams.mapper.PermissionMapper;
import com.cooxiao.mall.ams.service.IPermissionService;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.admin.dto.PermissionAddDTO;
import com.cooxiao.mall.pojo.admin.dto.PermissionUpdateDTO;
import com.cooxiao.mall.pojo.admin.model.Permission;
import com.cooxiao.mall.pojo.admin.query.PermissionQuery;
import com.cooxiao.mall.pojo.admin.vo.PermissionVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 权限表 服务实现类
 * </p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
@Service
public class PermissionServiceImpl implements IPermissionService {
    @Autowired
    private PermissionMapper permissionMapper;
    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    private PermissionVO convertToVO(Permission permission) {
        if (permission == null) {
            return null;
        }
        PermissionVO vo = new PermissionVO();
        BeanUtils.copyProperties(permission, vo);
        return vo;
    }

    @Override
    public void addPermission(PermissionAddDTO permissionAddDTO) {
        //转化对象
        Permission permission=new Permission();
        BeanUtils.copyProperties(permissionAddDTO,permission);
        permissionMapper.insertPermission(permission);
    }

    @Override
    public JsonPage<PermissionVO> listPermissions(PermissionQuery permissionQuery) {
        Page<Permission> page = new Page<>(permissionQuery.getPageNum(), permissionQuery.getSizeNum());
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        if (permissionQuery.getName() != null) {
            wrapper.like(Permission::getName, permissionQuery.getName());
        }
        if (permissionQuery.getValue() != null) {
            wrapper.like(Permission::getValue, permissionQuery.getValue());
        }
        Page<Permission> result = permissionMapper.selectPage(page, wrapper);
        List<PermissionVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        IPage<PermissionVO> pageVO = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageVO.setRecords(voList);
        return JsonPage.restPage(pageVO);
    }

    @Override
    public void updatePermission(PermissionUpdateDTO permissionUpdateDTO) {
        Permission permission=new Permission();
        BeanUtils.copyProperties(permissionUpdateDTO,permission);
        permissionMapper.updatePermission(permission);
    }

    @Override
    public void deletePermission(Long id) {
        //检查是否有关联
        int count=rolePermissionMapper.selectExistByPermissionId(id);
        if(count!=0){
            throw new CoolSharkException("当前权限有关联角色不可删除",409);
        }
        permissionMapper.deletePermission(id);
    }
}

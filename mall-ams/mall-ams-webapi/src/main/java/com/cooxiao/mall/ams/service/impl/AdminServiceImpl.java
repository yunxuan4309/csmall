package com.cooxiao.mall.ams.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cooxiao.mall.ams.exception.CoolSharkException;
import com.cooxiao.mall.ams.mapper.AdminRoleMapper;
import com.cooxiao.mall.ams.mapper.AdminMapper;
import com.cooxiao.mall.ams.service.IAdminService;
import com.cooxiao.mall.ams.utils.IdGeneratorUtils;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.admin.dto.AdminAddDTO;
import com.cooxiao.mall.pojo.admin.dto.AdminUpdateDTO;
import com.cooxiao.mall.pojo.admin.model.Admin;
import com.cooxiao.mall.pojo.admin.vo.AdminVO;
import com.alibaba.druid.util.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 管理员表 服务实现类
 * </p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
@Service
public class AdminServiceImpl implements IAdminService {


    @Autowired
    private AdminMapper adminMapper;
    @Autowired
    private AdminRoleMapper adminRoleMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Override
    public void addAdmin(AdminAddDTO adminDTO) {
        checkPassword(adminDTO.getPassword(),adminDTO.getPasswordAct());
        //转化bean
        Admin admin= new Admin();
        BeanUtils.copyProperties(adminDTO,admin);
        //补充id
        Long id= IdGeneratorUtils.generatId("admin");
        admin.setId(id);
        //密码加密
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        adminMapper.insertAdmin(admin);
    }

    @Override
    public JsonPage<AdminVO> queryAdmins(Integer pageNum, Integer sizeNum, String query) {
        if(StringUtils.isEmpty(query)){
            return listAdmins(pageNum,sizeNum);
        }
        Page<Admin> page = new Page<>(pageNum, sizeNum);
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Admin::getUsername, query)
               .or()
               .like(Admin::getNickname, query);
        Page<Admin> result = adminMapper.selectPage(page, wrapper);
        List<AdminVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        IPage<AdminVO> pageVO = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageVO.setRecords(voList);
        return JsonPage.restPage(pageVO);
    }

    @Override
    public void updateAdmin(AdminUpdateDTO adminUpdateDTO) {
        Admin admin=new Admin();
        BeanUtils.copyProperties(adminUpdateDTO,admin);
        checkPassword(adminUpdateDTO.getPassword(),adminUpdateDTO.getPasswordAct());
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        adminMapper.updateAdmin(admin);
    }

    @Override
    public void deleteAdmin(Long id) {
        adminMapper.deleteAdmin(id);
        adminRoleMapper.deleteAdminRole(id);
    }

    @Override
    public AdminVO queryOneAdmin(String username) {
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Admin::getUsername, username);
        Admin admin = adminMapper.selectOne(wrapper);
        return convertToVO(admin);
    }

    //检查密码
    private void checkPassword(String password,String passwordAct) {
        if(!StringUtils.equals(password,passwordAct)){
            throw new CoolSharkException("两次输入密码不正确",400);
        }
    }

    // 转换为VO
    private AdminVO convertToVO(Admin admin) {
        if (admin == null) {
            return null;
        }
        AdminVO vo = new AdminVO();
        BeanUtils.copyProperties(admin, vo);
        return vo;
    }

    @Override
    public JsonPage<AdminVO> listAdmins(Integer pageNum, Integer sizeNum) {
        Page<Admin> page = new Page<>(pageNum, sizeNum);
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Admin::getGmtCreate);
        Page<Admin> result = adminMapper.selectPage(page, wrapper);
        List<AdminVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        IPage<AdminVO> pageVO = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageVO.setRecords(voList);
        return JsonPage.restPage(pageVO);
    }
}

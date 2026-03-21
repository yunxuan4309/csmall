package com.cooxiao.mall.ams.service.impl;

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
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

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
        PageHelper.startPage(pageNum,sizeNum);
        List<AdminVO> adminVOList=adminMapper.selectAdminsByUsername(query);
        PageInfo pageInfo=PageInfo.of(adminVOList);
        return JsonPage.restPage(pageInfo);
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
        return adminMapper.selectAdminByUsername(username);
    }

    //检查密码
    private void checkPassword(String password,String passwordAct) {
        if(!StringUtils.equals(password,passwordAct)){
            throw new CoolSharkException("两次输入密码不正确",400);
        }
    }

    @Override
    public JsonPage<AdminVO> listAdmins(Integer pageNum, Integer sizeNum) {
        PageHelper.startPage(pageNum,sizeNum);
        List<AdminVO> adminVOList=adminMapper.selectAdmins();
        PageInfo pageInfo=PageInfo.of(adminVOList);
        return JsonPage.restPage(pageInfo);

    }
}

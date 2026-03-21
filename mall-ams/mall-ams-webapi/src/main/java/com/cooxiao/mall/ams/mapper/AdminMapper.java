package com.cooxiao.mall.ams.mapper;

import com.cooxiao.mall.pojo.admin.model.Admin;
import com.cooxiao.mall.pojo.admin.vo.AdminVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * <p> 管理员表 Mapper 接口</p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
public interface AdminMapper extends BaseMapper<Admin> {
    void insertAdmin(Admin admin);

    List<AdminVO> selectAdmins();

    List<AdminVO> selectAdminsByUsername(String query);

    AdminVO selectAdminByUsername(String username);

    void updateAdmin(Admin admin);

    void deleteAdmin(Long id);
}

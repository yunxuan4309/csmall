package com.cooxiao.mall.ams.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cooxiao.mall.pojo.admin.model.Admin;
import org.apache.ibatis.annotations.Param;

/**
 * <p> 管理员表 Mapper 接口</p>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
public interface AdminMapper extends BaseMapper<Admin> {
    void insertAdmin(Admin admin);

    void updateAdmin(Admin admin);

    void deleteAdmin(Long id);
}

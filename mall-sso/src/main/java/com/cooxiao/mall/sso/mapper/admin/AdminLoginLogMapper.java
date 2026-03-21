package com.cooxiao.mall.sso.mapper.admin;

import com.cooxiao.mall.pojo.admin.model.AdminLoginLog;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminLoginLogMapper {
    /**
     * 记录登录admin日志
     * @param adminLoginLog
     */
    void insertAdminLoginLog(AdminLoginLog adminLoginLog);
}

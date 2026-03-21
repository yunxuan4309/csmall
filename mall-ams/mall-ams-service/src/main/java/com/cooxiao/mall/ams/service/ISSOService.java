package com.cooxiao.mall.ams.service;

import com.cooxiao.mall.pojo.admin.model.Admin;

import java.io.IOException;

public interface ISSOService {
    String doLogin(Admin admin) throws IOException;
}

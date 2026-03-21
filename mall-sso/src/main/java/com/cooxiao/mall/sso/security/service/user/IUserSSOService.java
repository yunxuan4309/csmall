package com.cooxiao.mall.sso.security.service.user;

import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.sso.pojo.dto.UserLoginDTO;


public interface IUserSSOService {
    String doLogin(UserLoginDTO userLoginDTO) throws CoolSharkServiceException;

    void doLogout(String token) throws CoolSharkServiceException;
}

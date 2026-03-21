package com.cooxiao.mall.sso.service;

import com.cooxiao.mall.sso.pojo.vo.UserInfoVO;

public interface IUserInfoService {
    UserInfoVO userInfo(String authToken);
}

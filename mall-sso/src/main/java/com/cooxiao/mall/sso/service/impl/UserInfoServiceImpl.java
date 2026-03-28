package com.cooxiao.mall.sso.service.impl;

import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.common.utils.JwtTokenUtils;
import com.cooxiao.mall.pojo.admin.vo.AdminVO;
import com.cooxiao.mall.pojo.ums.vo.UserVO;
import com.cooxiao.mall.sso.mapper.admin.AdminMapper;
import com.cooxiao.mall.sso.mapper.user.UserMapper;
import com.cooxiao.mall.sso.pojo.vo.UserInfoVO;
import com.cooxiao.mall.sso.service.IUserInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserInfoServiceImpl implements IUserInfoService {
    @Autowired
    private JwtTokenUtils jwtTokenUtils;
    @Autowired
    private AdminMapper adminMapper;
    @Autowired
    private UserMapper userMapper;

    private static final String jwtTokenHead ="Bearer";
    @Override
    public UserInfoVO userInfo(String authToken) {
        String token=authToken.substring(jwtTokenHead.length());
        log.info("获取token:{}",token);
        CsmallAuthenticationInfo userInfo = jwtTokenUtils.getUserInfo(token);
        String type=userInfo.getUserType();
        Long id=userInfo.getId();
        //准备返回数据UserInfoVO
        UserInfoVO userInfoVO=new UserInfoVO();
        if (type!=null&&"ADMIN".equals(type)){
            AdminVO adminVO=adminMapper.findById(id);
            userInfoVO.setUserId(id);
            userInfoVO.setNickname(adminVO.getNickname());
            userInfoVO.setPhone(adminVO.getPhone());
            userInfoVO.setUsername(adminVO.getUsername());
        }else if(type!=null&&"USER".equals(type)){
            UserVO userVO=userMapper.findById(id);
            userInfoVO.setUserId(id);
            userInfoVO.setNickname(userVO.getNickname());
            userInfoVO.setPhone(userVO.getPhone());
            userInfoVO.setUsername(userVO.getUsername());
        }else{
            throw new CoolSharkServiceException(ResponseCode.BAD_REQUEST,"无法获取用户信息");
        }
        return userInfoVO;
    }
}

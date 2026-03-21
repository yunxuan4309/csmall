package com.cooxiao.mall.ums.service;

import com.cooxiao.mall.pojo.ums.dto.UserDetailAddDTO;
import com.cooxiao.mall.pojo.ums.dto.UserDetailUpdateDTO;
import com.cooxiao.mall.pojo.ums.vo.UserDetailStandardVO;

/**
 * <p>
 * 用户详细（不常用）信息表 服务类
 * </p>
 *
 * @author cooxiao.com
 * @since 2022-02-22
 */
public interface IUserDetailService{

    void addUserDetail(UserDetailAddDTO userDetailAddDTO);

    UserDetailStandardVO getUserDetails();

    void updateUserDetail(UserDetailUpdateDTO userDetailUpdateDTO);
}

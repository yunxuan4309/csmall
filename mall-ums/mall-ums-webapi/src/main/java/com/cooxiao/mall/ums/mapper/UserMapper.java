package com.cooxiao.mall.ums.mapper;

import com.cooxiao.mall.pojo.ums.model.User;
import com.cooxiao.mall.pojo.ums.vo.UserVO;
import org.apache.ibatis.annotations.Param;

/**
 * <p> 用户基本（常用）信息表 Mapper 接口</p>
 *
 * @author cooxiao.com
 * @since 2022-02-22
 */
public interface UserMapper {
    UserVO selectUserByUsername(String username);

    void insertUser(User user);

    int selectExistByUsernameOrPhoneOrEmail(@Param("value") String value, @Param("type") String type);

    User selectUserById(@Param("userId")Long userId);

    void updatePasswordById(@Param("userId")Long userId, @Param("newPassword")String encodedNewPassword);
}

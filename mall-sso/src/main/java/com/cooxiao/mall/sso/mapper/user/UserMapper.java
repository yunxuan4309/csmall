package com.cooxiao.mall.sso.mapper.user;

import com.cooxiao.mall.pojo.ums.model.User;
import com.cooxiao.mall.pojo.ums.vo.UserVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
@Qualifier("db2SqlSessionTemplate")
public interface UserMapper {
    User findByUsername(String username);

    UserVO findById(Long id);
}

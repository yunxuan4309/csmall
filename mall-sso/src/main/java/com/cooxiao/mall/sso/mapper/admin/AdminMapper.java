package com.cooxiao.mall.sso.mapper.admin;

import com.cooxiao.mall.pojo.admin.model.Admin;
import com.cooxiao.mall.pojo.admin.vo.AdminVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

/**
 * <p> 管理员表 Mapper 接口</p>
 *
 * <p>2021-12-14修改</p>
 * <ul>
 *     <li>代码排版，添加注释</li>
 *     <li>添加@Repository注解</li>
 *     <li>修改方法名</li>
 * </ul>
 *
 * @author cooxiao.com
 * @since 2021-12-02
 */
@Repository
@Qualifier("db1SqlSessionTemplate")
public interface AdminMapper{

    /**
     * 根据管理员用户名查询管理员详情
     *
     * @param username 管理员用户名
     * @return 匹配的管理员详情，如果没有匹配的数据，则返回null
     */
    Admin findByUsername(@Param("username") String username);

    AdminVO findById(Long id);
}

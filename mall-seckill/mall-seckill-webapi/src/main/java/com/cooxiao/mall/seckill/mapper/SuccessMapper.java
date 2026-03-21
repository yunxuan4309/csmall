package com.cooxiao.mall.seckill.mapper;

import com.cooxiao.mall.pojo.seckill.model.Success;
import org.springframework.stereotype.Repository;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/10/10
 */
@Repository
public interface SuccessMapper {
    // 新增Success对象到数据库的方法
    int saveSuccess(Success success);
}

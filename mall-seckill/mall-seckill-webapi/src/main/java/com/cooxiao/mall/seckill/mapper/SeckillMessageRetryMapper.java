package com.cooxiao.mall.seckill.mapper;

import com.cooxiao.mall.pojo.seckill.model.SeckillMessageRetry;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeckillMessageRetryMapper {

    /** 插入消息记录 */
    int insert(SeckillMessageRetry retry);

    /** 更新状态为已发送 */
    int updateStatusSent(@Param("id") Long id);

    /** 重试次数+1，并更新错误信息 */
    int incrementRetry(@Param("id") Long id, @Param("errorMsg") String errorMsg);

    /** 标记为最终失败 */
    int updateStatusFailed(@Param("id") Long id);

    /** 查询待重试的消息（status=0 且 retryCount < maxRetries） */
    List<SeckillMessageRetry> selectPending(@Param("maxRetries") int maxRetries);
}

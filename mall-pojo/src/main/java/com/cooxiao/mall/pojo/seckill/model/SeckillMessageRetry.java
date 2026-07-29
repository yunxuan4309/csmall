package com.cooxiao.mall.pojo.seckill.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("seckill_message_retry")
public class SeckillMessageRetry implements Serializable {

    @TableId
    private Long id;

    /** 用户ID */
    private Long userId;

    /** SKU ID */
    private Long skuId;

    /** 订单编号 */
    private String orderSn;

    /** Success 对象 JSON */
    private String messageBody;

    /** 0-待发送 1-已发送 2-失败达上限 */
    private Integer status;

    /** 重试次数 */
    private Integer retryCount;

    /** 最近一次错误信息 */
    private String errorMsg;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}

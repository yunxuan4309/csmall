package com.cooxiao.mall.pojo.seckill.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author cooxiao.com
 * @since 2022-02-23
 */
@Data
@TableName("seckill_spu")
public class SeckillSpu implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long spuId;

    private BigDecimal listPrice;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;


}

package com.cooxiao.mall.ums.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cooxiao.mall.pojo.ums.model.DeliveryAddress;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <p> 用户收货地址表 Mapper 接口</p>
 *
 * @author cooxiao.com
 * @since 2022-02-22
 */
@Repository
public interface DeliveryAddressMapper extends BaseMapper<DeliveryAddress> {
    void insertDeliveryAddress(DeliveryAddress deliveryAddress);

    int selectCountByUserId(@Param("userId")Long userId);

    void updateAddressById(DeliveryAddress deliveryAddress);

    void deleteAddressById(Long id);
}

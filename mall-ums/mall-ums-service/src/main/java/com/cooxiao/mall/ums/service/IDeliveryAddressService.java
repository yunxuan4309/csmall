package com.cooxiao.mall.ums.service;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.ums.dto.DeliveryAddressAddDTO;
import com.cooxiao.mall.pojo.ums.dto.DeliveryAddressEditDTO;
import com.cooxiao.mall.pojo.ums.vo.DeliveryAddressStandardVO;

/**
 * <p>
 * 用户收货地址表 服务类
 * </p>
 *
 * @author cooxiao.com
 * @since 2022-02-22
 */

public interface IDeliveryAddressService{

    JsonPage<DeliveryAddressStandardVO> listAddress(Integer page, Integer pageSize);

    void addAddress(DeliveryAddressAddDTO deliveryAddressAddDTO);

    void editAddress(DeliveryAddressEditDTO deliveryAddressEditDTO);

    void deleteAddress(Long id);
}

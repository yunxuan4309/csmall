package com.cooxiao.mall.ums.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.ums.dto.DeliveryAddressAddDTO;
import com.cooxiao.mall.pojo.ums.dto.DeliveryAddressEditDTO;
import com.cooxiao.mall.pojo.ums.model.DeliveryAddress;
import com.cooxiao.mall.pojo.ums.vo.DeliveryAddressStandardVO;
import com.cooxiao.mall.ums.mapper.DeliveryAddressMapper;
import com.cooxiao.mall.ums.service.IDeliveryAddressService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户收货地址表 服务实现类
 * </p>
 *
 * @author cooxiao.com
 * @since 2022-02-22
 */
@Service
public class DeliveryAddressServiceImpl implements IDeliveryAddressService {
    @Autowired
    private DeliveryAddressMapper deliveryAddressMapper;

    private DeliveryAddressStandardVO convertToVO(DeliveryAddress address) {
        if (address == null) {
            return null;
        }
        DeliveryAddressStandardVO vo = new DeliveryAddressStandardVO();
        BeanUtils.copyProperties(address, vo);
        return vo;
    }

    @Override
    public JsonPage<DeliveryAddressStandardVO> listAddress(Integer page, Integer pageSize) {
        Long userId = getUserId();
        Page<DeliveryAddress> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<DeliveryAddress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeliveryAddress::getUserId, userId)
               .orderByDesc(DeliveryAddress::getGmtModified);
        Page<DeliveryAddress> result = deliveryAddressMapper.selectPage(pageParam, wrapper);
        List<DeliveryAddressStandardVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        IPage<DeliveryAddressStandardVO> pageVO = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageVO.setRecords(voList);
        return JsonPage.restPage(pageVO);
    }

    @Override
    public void addAddress(DeliveryAddressAddDTO deliveryAddressAddDTO) {
        // 业务校验：手机号/固定电话至少填一个（DTO 注解无法表达"二选一"，在此兜底）
        boolean hasMobile = deliveryAddressAddDTO.getMobilePhone() != null
                && !deliveryAddressAddDTO.getMobilePhone().isBlank();
        boolean hasTelephone = deliveryAddressAddDTO.getTelephone() != null
                && !deliveryAddressAddDTO.getTelephone().isBlank();
        if (!hasMobile && !hasTelephone) {
            throw new CoolSharkServiceException(
                    ResponseCode.BAD_REQUEST, "新增地址失败，手机号与固定电话至少填写一个！");
        }
        //转化数据
        Long userId = getUserId();
        DeliveryAddress deliveryAddress=new DeliveryAddress();
        //查询已存在
        LambdaQueryWrapper<DeliveryAddress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeliveryAddress::getUserId, userId);
        long count = deliveryAddressMapper.selectCount(wrapper);
        if(count==0){
            //说明之前米有写入任何地址管理,第一个地址就是默认地址
            deliveryAddress.setDefaultAddress(1);
        }else{
            deliveryAddress.setDefaultAddress(0);
        }
        BeanUtils.copyProperties(deliveryAddressAddDTO,deliveryAddress);
        deliveryAddress.setUserId(userId);
        deliveryAddressMapper.insert(deliveryAddress);
    }

    @Override
    public void editAddress(DeliveryAddressEditDTO deliveryAddressEditDTO) {
        // 编辑为动态更新：不强制手机/固话二选一（可能只改地址不碰联系方式），
        // 但若传了任一为空字符串则视为非法
        if (deliveryAddressEditDTO.getMobilePhone() != null
                && deliveryAddressEditDTO.getMobilePhone().isBlank()) {
            throw new CoolSharkServiceException(
                    ResponseCode.BAD_REQUEST, "编辑地址失败，联系电话不能为空字符串！");
        }
        if (deliveryAddressEditDTO.getTelephone() != null
                && deliveryAddressEditDTO.getTelephone().isBlank()) {
            throw new CoolSharkServiceException(
                    ResponseCode.BAD_REQUEST, "编辑地址失败，固定电话不能为空字符串！");
        }
        //转化
        DeliveryAddress deliveryAddress=new DeliveryAddress();
        BeanUtils.copyProperties(deliveryAddressEditDTO,deliveryAddress);
        deliveryAddressMapper.updateById(deliveryAddress);
    }

    @Override
    public void deleteAddress(Long id) {
        deliveryAddressMapper.deleteById(id);
    }

    //TODO 可以和购物车业务层方法合并简化
    public CsmallAuthenticationInfo getUserInfo(){
        //从security环境获取username,先拿到authentication
        UsernamePasswordAuthenticationToken authentication = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        //如果不是空的可以调用dubbo远程微服务获取adminVO
        if(authentication!=null){
            CsmallAuthenticationInfo csmallAuthenticationInfo=(CsmallAuthenticationInfo)authentication.getCredentials();
            return csmallAuthenticationInfo;
        }else{
            throw new CoolSharkServiceException(ResponseCode.UNAUTHORIZED,"没有登录者信息");
        }
    }
    public Long getUserId(){
        CsmallAuthenticationInfo userInfo = getUserInfo();
        return userInfo.getId();
    }
}

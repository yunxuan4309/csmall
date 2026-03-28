package com.cooxiao.mall.order.service.impl;

import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.order.mapper.OmsOrderItemMapper;
import com.cooxiao.mall.order.mapper.OmsOrderMapper;
import com.cooxiao.mall.order.service.IOmsCartService;
import com.cooxiao.mall.order.service.IOmsOrderService;
import com.cooxiao.mall.order.utils.IdGeneratorUtils;
import com.cooxiao.mall.pojo.order.dto.OrderAddDTO;
import com.cooxiao.mall.pojo.order.dto.OrderItemAddDTO;
import com.cooxiao.mall.pojo.order.dto.OrderListTimeDTO;
import com.cooxiao.mall.pojo.order.dto.OrderStateUpdateDTO;
import com.cooxiao.mall.pojo.order.model.OmsCart;
import com.cooxiao.mall.pojo.order.model.OmsOrder;
import com.cooxiao.mall.pojo.order.model.OmsOrderItem;
import com.cooxiao.mall.pojo.order.vo.OrderAddVO;
import com.cooxiao.mall.pojo.order.vo.OrderDetailVO;
import com.cooxiao.mall.pojo.order.vo.OrderListVO;
import com.cooxiao.mall.product.service.order.IForOrderSkuService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/25
 */
// 订单管理模块业务逻辑层实现需要作为Dubbo的生产者,为后面秒杀业务提供服务
@DubboService
@Service
@Slf4j
public class OmsOrderServiceImpl implements IOmsOrderService {

    // Dubbo调用product模块减少库存的方法
    @DubboReference
    private IForOrderSkuService dubboSkuService;
    @Autowired
    private IOmsCartService omsCartService;
    @Autowired
    private OmsOrderMapper omsOrderMapper;
    @Autowired
    private OmsOrderItemMapper omsOrderItemMapper;

    // 新增订单的方法
    // 这个方法因为会dubbo调用product模块减少库存的功能
    // 所以操作的数据库,有分布式事务的需求,需要用seata保证数据完整性
    @GlobalTransactional
    @Override
    public OrderAddVO addOrder(OrderAddDTO orderAddDTO) {
        // 第一部分:信息的收集
        // 先实例化订单信息对象,用于获取参数orderAddDTO中的同名属性
        OmsOrder order=new OmsOrder();
        BeanUtils.copyProperties(orderAddDTO,order);
        // orderAddDTO参数中属性并不是齐全的,还有一些信息需要我们生成,收集和验证
        // 所以编写一个专门的方法完成这个信息补齐的操作
        loadOrder(order);
        // 到此为止,order对象的所有属性收集完毕
        // 下面开始对参数orderAddDTO中包含的订单项集合:orderItems,进行信息的收集
        List<OrderItemAddDTO> orderItems=orderAddDTO.getOrderItems();
        // 判断当前参数包含的订单项集合是否为null
        if(orderItems == null || orderItems.isEmpty()){
            // 如果为null,终止新增订单的流程
            throw new CoolSharkServiceException(
                    ResponseCode.BAD_REQUEST,"订单中至少包含一件商品");
        }
        // 最终实现新增订单项集合到数据库的集合类型是List<OmsOrderItem>
        // 我们要做的是将上面集合中所有元素转换为OmsOrderItem类型,然后新增到下面集合中
        // 所以实例化OmsOrderItem这个类型的集合
        List<OmsOrderItem> omsOrderItems=new ArrayList<>();
        // 遍历前端传入的集合,也就是DTO泛型的集合
        for(OrderItemAddDTO addDTO : orderItems){
            // 先实例化OmsOrderItem对象
            OmsOrderItem omsOrderItem=new OmsOrderItem();
            // 将正在遍历的addDTO的同名属性赋值到omsOrderItem中
            BeanUtils.copyProperties(addDTO,omsOrderItem);
            // 将addDTO中没有的属性(id和orderId)赋值
            // id也是Leaf生成
            Long itemId= IdGeneratorUtils.getDistributeId("order_item");
            omsOrderItem.setId(itemId);
            // orderId属性直接中order对象中获取
            omsOrderItem.setOrderId(order.getId());
            // 将补全所有属性的omsOrderItem对象添加到循环前声明的集合中
            omsOrderItems.add(omsOrderItem);
            // 第二部分:数据库操作
            // 1.库存减少
            // 将正在遍历的对象关联的skuId库存,减少用户要购买的数量
            // 先获取skuId
            Long skuId=omsOrderItem.getSkuId();
            // dubbo调用product模块写好的减少库存的方法
            int rows=dubboSkuService
                    .reduceStockNum(skuId,omsOrderItem.getQuantity());
            if(rows==0){
                // 上面库存的修改没有影响数据库的话,就是库存不足的情况了
                log.error("商品库存不足,skuId:{}",skuId);
                // 抛出异常,终止流程,同时触发seata分布式事务回滚
                throw new CoolSharkServiceException(
                        ResponseCode.BAD_REQUEST,"库存不足");
            }
            // 2.删除用户在购物车中购物车中勾选的商品
            OmsCart omsCart=new OmsCart();
            omsCart.setUserId(order.getUserId());
            omsCart.setSkuId(skuId);
            // 执行删除
            omsCartService.removeUserCarts(omsCart);
        }
        // 3.执行新增订单
        omsOrderMapper.insertOrder(order);
        // 4.执行新增订单项
        omsOrderItemMapper.insertOrderItemList(omsOrderItems);
        // 第三部分:收集需要的返回值
        // 实例化返回值类型对象,为其赋值,最后返回
        OrderAddVO addVO=new OrderAddVO();
        addVO.setId(order.getId());
        addVO.setSn(order.getSn());
        addVO.setCreateTime(order.getGmtCreate());
        addVO.setPayAmount(order.getAmountOfActualPay());
        // 最后别忘了返回!!!
        return addVO;
    }
    // 给order对象补全所有属性的方法
    private void loadOrder(OmsOrder order) {
        // 先通过leaf获取当前order订单的id
        Long id= IdGeneratorUtils.getDistributeId("order");
        order.setId(id);
        // 生成一个给用户看的订单编号,是UUID
        order.setSn(UUID.randomUUID().toString());
        // 为userId赋值
        // 以后在秒杀业务中,需要秒杀模块为userId赋值,所有当前位置就不要赋值了
        // 要判断当前order中是否已经有userId
        if(order.getUserId() == null) {
            // 如果order对象的userId是null,再为userId赋值
            order.setUserId(getUserId());
        }

        // 其它可能为null的属性最好都赋个默认值
        // 这里以订单状态为例,判断state为null默认为0
        if(order.getState()==null){
            order.setState(0);
        }

        // 为了保证当前订单下单时间gmt_order和数据生成时间gmt_create一致
        // 我们为下列属性赋相同的值
        LocalDateTime now= LocalDateTime.now();
        order.setGmtOrder(now);
        order.setGmtCreate(now);
        order.setGmtModified(now);

        // 后端代码一般会对前端传入的金额进行验算
        // 实际支付金额公式:   实际支付金额=原价-优惠+运费
        // 使用的类是BigDecimal类型,防止出现浮点偏移现象
        BigDecimal price=order.getAmountOfOriginalPrice();
        BigDecimal freight=order.getAmountOfFreight();
        BigDecimal discount=order.getAmountOfDiscount();
        BigDecimal actualPay=price.subtract(discount).add(freight);

        // 赋值到order对象中
        order.setAmountOfActualPay(actualPay);

    }

    // 根据订单id,修改订单状态
    @Override
    public void updateOrderState(OrderStateUpdateDTO orderStateUpdateDTO) {
        // 先实例化OmsOrder对象
        OmsOrder order=new OmsOrder();
        // orderStateUpdateDTO参数属性只有id和state,实现修改订单状态,进行赋值
        BeanUtils.copyProperties(orderStateUpdateDTO,order);
        // 调用动态修改方法,因为参数中只有state有值,所以只是修改订单状态
        omsOrderMapper.updateOrderById(order);
    }

    // 分页查询指定时间区间,当前登录用户所有订单信息
    @Override
    public JsonPage<OrderListVO> listOrdersBetweenTimes(
            OrderListTimeDTO orderListTimeDTO) {
        // 方法开始,需要在运行查询前先判断用户给定的时间范围
        // 要判断orderListTimeDTO参数中的开始时间和结束时间,默认一个月内
        // 编写一个方法专门进行判断
        validateTimes(orderListTimeDTO);
        // 设置分页查询条件
        PageHelper.startPage(orderListTimeDTO.getPage(),
                orderListTimeDTO.getPageSize());
        // 给当前登录用户id赋值
        orderListTimeDTO.setUserId(getUserId());
        // 执行查询
        List<OrderListVO> list=omsOrderMapper
                .selectOrderBetweenTimes(orderListTimeDTO);
        //  别忘了返回
        return JsonPage.restPage(new PageInfo<>(list));
    }

    private void validateTimes(OrderListTimeDTO orderListTimeDTO) {
        // 先获取开始时间和结束时间对象
        LocalDateTime start=orderListTimeDTO.getStartTime();
        LocalDateTime end=orderListTimeDTO.getEndTime();
        // 判断start和end是否为null,如果有任何一个属性为null,查询最近一个月订单
        if(start==null  || end==null ){
            // 开始时间设置为一个月之前
            start=LocalDateTime.now().minusMonths(1);
            end=LocalDateTime.now();
            // 将设置好的值赋值到参数对象汇总
            orderListTimeDTO.setStartTime(start);
            orderListTimeDTO.setEndTime(end);
        }else{
            // 如果start和end都非null
            // 要判断end是否小于start,如果小于抛出异常
            // if(end.isBefore(start))
            if(end.toInstant(ZoneOffset.of("+8")).toEpochMilli()<
                    start.toInstant(ZoneOffset.of("+8")).toEpochMilli()){
                // 结束时间小于开始时间抛出异常
                throw new CoolSharkServiceException(
                        ResponseCode.BAD_REQUEST,"结束时间应大于开始时间");
            }
        }
    }


    @Override
    public OrderDetailVO getOrderDetail(Long id) {
        return null;
    }


    public CsmallAuthenticationInfo getUserInfo(){
        UsernamePasswordAuthenticationToken token=
                (UsernamePasswordAuthenticationToken)
                        SecurityContextHolder.getContext().getAuthentication();
        if(token == null){
            throw new CoolSharkServiceException(
                    ResponseCode.UNAUTHORIZED,"您还没有登录!");
        }
        CsmallAuthenticationInfo csmallAuthenticationInfo=
                (CsmallAuthenticationInfo) token.getCredentials();
        return csmallAuthenticationInfo;
    }

    public Long getUserId(){
        return getUserInfo().getId();
    }

}



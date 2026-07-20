package com.cooxiao.mall.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.order.mapper.OmsOrderItemMapper;
import com.cooxiao.mall.order.mapper.OmsOrderMapper;
import com.cooxiao.mall.order.mapper.OmsPaymentRecordMapper;
import com.cooxiao.mall.order.mq.OrderItemMessage;
import com.cooxiao.mall.order.mq.OrderQueueConfig;
import com.cooxiao.mall.order.payment.PaymentCallbackResult;
import com.cooxiao.mall.order.payment.PaymentResult;
import com.cooxiao.mall.order.payment.PaymentStrategyFactory;
import com.cooxiao.mall.order.service.IOmsCartService;
import com.cooxiao.mall.order.service.IOmsOrderService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cooxiao.mall.pojo.order.dto.OrderAddDTO;
import com.cooxiao.mall.pojo.order.dto.OrderItemAddDTO;
import com.cooxiao.mall.pojo.order.dto.OrderListTimeDTO;
import com.cooxiao.mall.pojo.order.dto.OrderStateUpdateDTO;
import com.cooxiao.mall.pojo.order.dto.PayOrderDTO;
import com.cooxiao.mall.pojo.order.enums.PaymentStatusEnum;
import com.cooxiao.mall.pojo.order.enums.PaymentTypeEnum;
import com.cooxiao.mall.pojo.order.model.OmsCart;
import com.cooxiao.mall.pojo.order.model.OmsOrder;
import com.cooxiao.mall.pojo.order.model.OmsOrderItem;
import com.cooxiao.mall.pojo.order.model.PaymentRecord;
import com.cooxiao.mall.pojo.order.vo.OrderAddVO;
import com.cooxiao.mall.pojo.order.vo.OrderDetailVO;
import com.cooxiao.mall.pojo.order.vo.OrderItemListVO;
import com.cooxiao.mall.pojo.order.vo.OrderListVO;
import com.cooxiao.mall.pojo.order.vo.PayOrderVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    @Autowired
    private IOmsCartService omsCartService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private OmsOrderMapper omsOrderMapper;
    @Autowired
    private OmsOrderItemMapper omsOrderItemMapper;
    @Autowired
    private OmsPaymentRecordMapper paymentRecordMapper;
    @Autowired
    private PaymentStrategyFactory paymentStrategyFactory;

    // 新增订单的方法。
    // 库存扣减改为 MQ 异步处理，不再需要 @GlobalTransactional。
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
        List<OmsOrderItem> omsOrderItems=new ArrayList<>();
        // MQ 消息体：异步扣减库存时使用
        List<OrderItemMessage> orderItemMessages=new ArrayList<>();
        // 遍历前端传入的集合
        for(OrderItemAddDTO addDTO : orderItems){
            OmsOrderItem omsOrderItem=new OmsOrderItem();
            BeanUtils.copyProperties(addDTO,omsOrderItem);
            Long itemId= IdWorker.getId();
            omsOrderItem.setId(itemId);
            omsOrderItem.setOrderId(order.getId());
            omsOrderItems.add(omsOrderItem);

            // 收集 MQ 消息（库存扣减改为异步）
            OrderItemMessage msg = new OrderItemMessage();
            msg.setSkuId(omsOrderItem.getSkuId());
            msg.setQuantity(omsOrderItem.getQuantity());
            msg.setOrderItemId(itemId);
            orderItemMessages.add(msg);

            // 删除用户在购物车中勾选的商品
            OmsCart omsCart=new OmsCart();
            omsCart.setUserId(order.getUserId());
            omsCart.setSkuId(omsOrderItem.getSkuId());
            omsCartService.removeUserCarts(omsCart);
        }
        // 3.执行新增订单
        omsOrderMapper.insertOrder(order);
        // 4.执行新增订单项
        omsOrderItemMapper.insertOrderItemList(omsOrderItems);
        // 5.发送 MQ 消息，异步扣减库存（削峰填谷）
        rabbitTemplate.convertAndSend(
                OrderQueueConfig.ORDER_EX, OrderQueueConfig.ORDER_RK, orderItemMessages);
        log.info("订单 {} 已发送库存扣减消息，共 {} 件商品", order.getSn(), orderItemMessages.size());
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
        // 通过MyBatis-Plus IdWorker生成当前order订单的id
        Long id= IdWorker.getId();
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
        // 如果状态变更为已支付(3)，自动设置支付时间
        if (order.getState() != null && order.getState() == 3) {
            order.setGmtPay(LocalDateTime.now());
        }
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
        Page<OrderListVO> page = new Page<>(orderListTimeDTO.getPage(), orderListTimeDTO.getPageSize());
        // 给当前登录用户id赋值
        orderListTimeDTO.setUserId(getUserId());
        // 执行查询
        IPage<OrderListVO> result = omsOrderMapper.selectOrderBetweenTimes(page, orderListTimeDTO);
        // 返回分页结果
        return JsonPage.restPage(result);
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


    // 支付订单
    @Override
    public PayOrderVO payOrder(PayOrderDTO payOrderDTO) {
        // 1.查询订单信息
        OmsOrder order = omsOrderMapper.selectOrderById(payOrderDTO.getId());
        if (order == null) {
            throw new CoolSharkServiceException(ResponseCode.BAD_REQUEST, "订单不存在");
        }
        // 2.校验订单状态，只有未支付(0)的订单才能支付
        if (order.getState() != 0) {
            throw new CoolSharkServiceException(ResponseCode.BAD_REQUEST, "当前订单状态不支持支付");
        }
        // 3.校验订单所属用户
        if (!order.getUserId().equals(getUserId())) {
            throw new CoolSharkServiceException(ResponseCode.FORBIDDEN, "无权操作此订单");
        }

        // 4.确定支付方式
        Integer paymentType = payOrderDTO.getPaymentType();
        if (paymentType == null) {
            paymentType = order.getPaymentType();
            if (paymentType == null) {
                paymentType = 0;
            }
        }

        // 5.通过策略工厂获取对应支付渠道，发起支付
        PaymentResult result = paymentStrategyFactory.getStrategy(paymentType)
                .initiatePayment(order);

        if (!result.getSuccess()) {
            throw new CoolSharkServiceException(ResponseCode.INTERNAL_SERVER_ERROR,
                    "支付发起失败: " + result.getErrorMsg());
        }

        boolean simulated = result.getPaymentForm() == null && result.getPaymentUrl() == null;

        // 6.记录支付流水
        PaymentRecord record = new PaymentRecord();
        record.setId(IdWorker.getId());
        record.setOrderId(order.getId());
        record.setOrderSn(order.getSn());
        record.setUserId(order.getUserId());
        record.setPaymentType(paymentType);
        record.setPayAmount(order.getAmountOfActualPay());
        record.setTradeNo(result.getTradeNo());
        record.setOutTradeNo(result.getOutTradeNo());
        record.setPaymentStatus(simulated ? PaymentStatusEnum.SUCCESS.getCode() : PaymentStatusEnum.PENDING.getCode());
        record.setGmtRequest(LocalDateTime.now());
        if (simulated) {
            record.setGmtPayment(LocalDateTime.now());
        }
        record.setGmtCreate(LocalDateTime.now());
        paymentRecordMapper.insertRecord(record);

        // 7.更新订单
        OmsOrder updateOrder = new OmsOrder();
        updateOrder.setId(order.getId());
        updateOrder.setPaymentType(paymentType);
        if (simulated) {
            updateOrder.setState(3);
            updateOrder.setGmtPay(LocalDateTime.now());
        }
        omsOrderMapper.updateOrderById(updateOrder);

        // 8.组装返回结果
        PayOrderVO payOrderVO = new PayOrderVO();
        payOrderVO.setId(order.getId());
        payOrderVO.setSn(order.getSn());
        payOrderVO.setPaymentType(paymentType);
        payOrderVO.setPayAmount(order.getAmountOfActualPay());
        payOrderVO.setState(simulated ? 3 : order.getState());
        payOrderVO.setPaymentForm(result.getPaymentForm());
        payOrderVO.setPaymentUrl(result.getPaymentUrl());
        return payOrderVO;
    }

    /**
     * 处理支付回调，更新订单状态和支付流水。
     * 由 PaymentCallbackController 调用，不对外暴露。
     */
    public void handlePaymentCallback(PaymentCallbackResult callbackResult) {
        if (!callbackResult.getVerified()) {
            log.warn("支付回调验签失败，忽略处理");
            return;
        }
        if (callbackResult.getTradeNo() == null && callbackResult.getOutTradeNo() == null) {
            return;
        }

        // 1.根据商户订单号查找订单
        OmsOrder order = null;
        PaymentRecord record = null;
        if (callbackResult.getOutTradeNo() != null) {
            order = omsOrderMapper.selectOrderBySn(callbackResult.getOutTradeNo());
        }
        if (order == null && callbackResult.getTradeNo() != null) {
            record = paymentRecordMapper.selectByTradeNo(callbackResult.getTradeNo());
            if (record != null) {
                order = omsOrderMapper.selectOrderById(record.getOrderId());
            }
        }
        if (order == null) {
            log.error("支付回调找不到对应订单，outTradeNo: {}, tradeNo: {}",
                    callbackResult.getOutTradeNo(), callbackResult.getTradeNo());
            return;
        }

        // 2.幂等检查：已支付的订单不再处理
        if (order.getState() == 3) {
            log.info("订单已支付，忽略重复回调，订单号: {}", order.getSn());
            return;
        }

        // 3.更新订单状态
        OmsOrder updateOrder = new OmsOrder();
        updateOrder.setId(order.getId());
        updateOrder.setState(3);
        updateOrder.setGmtPay(callbackResult.getGmtPayment() != null
                ? callbackResult.getGmtPayment() : LocalDateTime.now());
        omsOrderMapper.updateOrderById(updateOrder);

        // 4.更新支付流水
        if (record == null) {
            record = paymentRecordMapper.selectByOrderId(order.getId());
        }
        if (record != null) {
            record.setPaymentStatus(PaymentStatusEnum.SUCCESS.getCode());
            record.setTradeNo(callbackResult.getTradeNo());
            record.setGmtPayment(callbackResult.getGmtPayment() != null
                    ? callbackResult.getGmtPayment() : LocalDateTime.now());
            record.setBuyerInfo(callbackResult.getBuyerInfo());
            record.setCallbackLog(callbackResult.getRawData());
            paymentRecordMapper.updateRecordById(record);
        }
    }

    @Override
    public OrderDetailVO getOrderDetail(Long id) {
        // 根据订单id查询订单信息
        OmsOrder order = omsOrderMapper.selectOrderById(id);
        if(order == null){
            return null;
        }
        // 根据订单id查询订单项列表
        List<OmsOrderItem> orderItems = omsOrderItemMapper.selectOrderItemsByOrderId(id);
        // 转换为VO类型
        List<OrderItemListVO> orderItemVOs = new ArrayList<>();
        for(OmsOrderItem item : orderItems){
            OrderItemListVO vo = new OrderItemListVO();
            BeanUtils.copyProperties(item, vo);
            orderItemVOs.add(vo);
        }
        // 创建OrderDetailVO并赋值
        OrderDetailVO detailVO = new OrderDetailVO();
        BeanUtils.copyProperties(order, detailVO);
        detailVO.setOrderItems(orderItemVOs);
        return detailVO;
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

package com.cooxiao.mall.order.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cooxiao.mall.common.annotation.Idempotent;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.order.exception.OrderBlockHandler;
import com.cooxiao.mall.order.payment.PaymentCallbackResult;
import com.cooxiao.mall.order.payment.PaymentStrategyFactory;
import com.cooxiao.mall.order.service.IOmsOrderService;
import com.cooxiao.mall.order.service.impl.OmsOrderServiceImpl;
import com.cooxiao.mall.pojo.order.dto.OrderAddDTO;
import com.cooxiao.mall.pojo.order.dto.OrderListTimeDTO;
import com.cooxiao.mall.pojo.order.dto.OrderStateUpdateDTO;
import com.cooxiao.mall.pojo.order.dto.PayOrderDTO;
import com.cooxiao.mall.pojo.order.vo.OrderAddVO;
import com.cooxiao.mall.pojo.order.vo.OrderDetailVO;
import com.cooxiao.mall.pojo.order.vo.OrderListVO;
import com.cooxiao.mall.pojo.order.vo.PayOrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/25
 */
@RestController
@RequestMapping("/oms/order")
@Api(tags = "02. 订单管理模块")
public class OmsOrderController {
    @Autowired
    private IOmsOrderService omsOrderService;
    @Autowired
    private OmsOrderServiceImpl omsOrderServiceImpl;
    @Autowired
    private PaymentStrategyFactory paymentStrategyFactory;

    @PostMapping("/add")
    @ApiOperation("执行新增订单的方法")
    @PreAuthorize("hasRole('user')")
    @Idempotent(key = "order", expire = 5, message = "订单正在提交中，请勿重复下单")
    @SentinelResource(value = "新增订单",
            blockHandlerClass = OrderBlockHandler.class, blockHandler = "addOrderBlock")
    public JsonResult<OrderAddVO> addOrder(@Validated @RequestBody OrderAddDTO orderAddDTO){
        OrderAddVO orderAddVO=omsOrderService.addOrder(orderAddDTO);
        return JsonResult.ok(orderAddVO);
    }

    @GetMapping("/list")
    @ApiOperation("分页查询当前用户指定时间内订单信息")
    @PreAuthorize("hasRole('user')")
    public JsonResult<JsonPage<OrderListVO>> listUserOrders(
            OrderListTimeDTO orderListTimeDTO){
        JsonPage<OrderListVO> jsonPage=
                omsOrderService.listOrdersBetweenTimes(orderListTimeDTO);
        return JsonResult.ok(jsonPage);
    }

    @PostMapping("/update/state")
    @ApiOperation("根据订单id修改订单状态")
    @PreAuthorize("hasRole('user')")
    public JsonResult updateOrderState(@Validated @RequestBody OrderStateUpdateDTO orderStateUpdateDTO){
        omsOrderService.updateOrderState(orderStateUpdateDTO);
        return JsonResult.ok("修改完成!");
    }

    @PostMapping("/pay")
    @ApiOperation("支付订单（当前为模拟支付，后续将接入微信/支付宝）")
    @PreAuthorize("hasRole('user')")
    @Idempotent(key = "pay", expire = 10, message = "支付正在处理中，请勿重复支付")
    @SentinelResource(value = "支付订单",
            blockHandlerClass = OrderBlockHandler.class, blockHandler = "payOrderBlock")
    public JsonResult<PayOrderVO> payOrder(@Validated @RequestBody PayOrderDTO payOrderDTO) {
        PayOrderVO payOrderVO = omsOrderService.payOrder(payOrderDTO);
        return JsonResult.ok(payOrderVO);
    }

    @GetMapping("/detail")
    @ApiOperation("根据订单id查询订单详情")
    @PreAuthorize("hasRole('user')")
    public JsonResult<OrderDetailVO> getOrderDetail(
            @ApiParam(value = "订单id", required = true) @RequestParam Long id) {
        OrderDetailVO detail = omsOrderService.getOrderDetail(id);
        return JsonResult.ok(detail);
    }

    @PostMapping("/pay/query")
    @ApiOperation("主动查询支付结果（前端从支付宝页面跳回后调用）")
    @PreAuthorize("hasRole('user')")
    public JsonResult<PayOrderVO> queryPayment(
            @ApiParam(value = "订单id", required = true) @RequestParam Long id,
            @ApiParam(value = "支付方式", required = true) @RequestParam Integer paymentType) {
        // 拿到订单的 sn，调用支付宝查询接口
        OrderDetailVO order = omsOrderService.getOrderDetail(id);
        if (order == null) {
            return JsonResult.ok(null);
        }
        // 已是已支付状态，直接返回
        if (order.getState() != null && order.getState() == 3) {
            return JsonResult.ok(null);
        }
        // 调用支付宝查单
        PaymentCallbackResult result = paymentStrategyFactory
                .getStrategy(paymentType)
                .queryPayment(order.getSn());
        // 如果已支付，更新订单和流水
        if (result.getVerified()) {
            omsOrderServiceImpl.handlePaymentCallback(result);
        }
        // 返回最新订单状态
        PayOrderVO vo = new PayOrderVO();
        vo.setId(order.getId());
        vo.setSn(order.getSn());
        vo.setPaymentType(paymentType);
        vo.setPayAmount(order.getAmountOfActualPay());
        vo.setState(result.getVerified() ? 3 : order.getState());
        return JsonResult.ok(vo);
    }

}

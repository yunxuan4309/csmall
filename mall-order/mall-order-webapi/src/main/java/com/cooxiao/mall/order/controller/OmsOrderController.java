package com.cooxiao.mall.order.controller;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.order.service.IOmsOrderService;
import com.cooxiao.mall.pojo.order.dto.OrderAddDTO;
import com.cooxiao.mall.pojo.order.dto.OrderListTimeDTO;
import com.cooxiao.mall.pojo.order.dto.OrderStateUpdateDTO;
import com.cooxiao.mall.pojo.order.vo.OrderAddVO;
import com.cooxiao.mall.pojo.order.vo.OrderDetailVO;
import com.cooxiao.mall.pojo.order.vo.OrderListVO;
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

    @PostMapping("/add")
    @ApiOperation("执行新增订单的方法")
    @PreAuthorize("hasRole('user')")
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

    @GetMapping("/detail")
    @ApiOperation("根据订单id查询订单详情")
    @PreAuthorize("hasRole('user')")
    public JsonResult<OrderDetailVO> getOrderDetail(
            @ApiParam(value = "订单id", required = true) @RequestParam Long id) {
        OrderDetailVO detail = omsOrderService.getOrderDetail(id);
        return JsonResult.ok(detail);
    }

}

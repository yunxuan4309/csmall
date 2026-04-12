package com.cooxiao.mall.order.controller;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.order.service.IOmsCartService;
import com.cooxiao.mall.order.utils.WebConsts;
import com.cooxiao.mall.pojo.order.dto.CartAddDTO;
import com.cooxiao.mall.pojo.order.dto.CartUpdateDTO;
import com.cooxiao.mall.pojo.order.vo.CartStandardVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/14
 */
@RestController
@RequestMapping("/oms/cart")
@Api(tags = "01. 购物车管理模块")
public class OmsCartController {
    @Autowired
    private IOmsCartService omsCartService;

    @PostMapping("/add")
    @ApiOperation("新增sku信息到购物车")
    // @PreAuthorize会验证当前SpringSecurity上下文是否有用户信息
    // 如果没有会发生401错误,提示没有登录,如果有用户信息,会验证是否包含ROLE_user权限
    // 酷鲨商城前台用户登录统一包含ROLE_user权限
    //该权限在sso的UserSSOController /login时注入到userUserDetail里面了
    // 所以下面注解实际意义就是判断用户是否已经登录
    @PreAuthorize("hasAuthority('ROLE_user')")
    // @Validated注解激活SpringValidation框架验证参数的功能
    // CartAddDTO参数中如果规定非null的属性出现null值,就会引发异常
    // 抛出BindException,由全局异常处理类处理
    public JsonResult addCart(@Validated @RequestBody CartAddDTO cartAddDTO){
        omsCartService.addCart(cartAddDTO);
        return JsonResult.ok("新增sku操作完成");
    }

    @PostMapping("/delete")
    @ApiOperation("根据id数组删除购物车中商品")
    @ApiImplicitParam(value = "要删除的id数组",name="ids",
            required = true,dataType = "array")
    @PreAuthorize("hasAuthority('ROLE_user')")
    public JsonResult removeCartsByIds(Long[] ids){
        omsCartService.removeCart(ids);
        return JsonResult.ok("删除完成!");
    }

    @PostMapping("/delete/all")
    @ApiOperation("清空当前登录用户的购物车商品")
// SpringSecurity框架保存登录用户权限信息,如果有权限信息使用ROLE_开头,表示实际上这是个角色
// @PreAuthorize注解就提供了更加快捷的编写方式判断用户是否具备某个角色
// ("hasRole")这个判断是专门判断以ROLE_开头的角色用的
// 判断时会自动在给定文本前添加ROLE_ 最终结果"hasRole('user')"->"hasAuthority('ROLE_user')"
// @PreAuthorize("hasAuthority('ROLE_user')")
    @PreAuthorize("hasRole('user')")
    public JsonResult removeCartsByUserId(){
        omsCartService.removeAllCarts();
        return JsonResult.ok("购物车已清空!");
    }

    @PostMapping("/update/quantity")
    @ApiOperation("修改购物车中sku商品数量")
    @PreAuthorize("hasRole('user')")
    public JsonResult updateQuantity(@Validated @RequestBody CartUpdateDTO cartUpdateDTO){
        omsCartService.updateQuantity(cartUpdateDTO);
        return JsonResult.ok("修改完成!");
    }

    @GetMapping("/list")
    @ApiOperation("根据用户Id分页查询购物车sku列表")
    @ApiImplicitParams({
            @ApiImplicitParam(value = "页码",name="page",example = "1"),
            @ApiImplicitParam(value = "每页条数",name="pageSize",example = "3")
    })
// 验证用户已经登录
    @PreAuthorize("hasAuthority('ROLE_user')")
    public JsonResult<JsonPage<CartStandardVO>> listCartByPage(
            @RequestParam(required = false,defaultValue = WebConsts.DEFAULT_PAGE)
            Integer page,
            @RequestParam(required = false,defaultValue = WebConsts.DEFAULT_PAGE_SIZE)
            Integer pageSize){
        JsonPage<CartStandardVO> jsonPage=
                omsCartService.listCarts(page,pageSize);
        return JsonResult.ok(jsonPage);
    }
}

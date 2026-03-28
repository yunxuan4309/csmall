package com.cooxiao.mall.order.service.impl;

import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.order.mapper.OmsCartMapper;
import com.cooxiao.mall.order.service.IOmsCartService;
import com.cooxiao.mall.pojo.order.dto.CartAddDTO;
import com.cooxiao.mall.pojo.order.dto.CartUpdateDTO;
import com.cooxiao.mall.pojo.order.model.OmsCart;
import com.cooxiao.mall.pojo.order.vo.CartStandardVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/14
 */

@Service
public class OmsCartServiceImpl implements IOmsCartService {
    @Autowired
    private OmsCartMapper omsCartMapper;

    // 新增用户选中的sku信息到购物车
    @Override
    public void addCart(CartAddDTO cartDTO) {
        // 实现新增信息到购物车,首先要判断当前用户购物车中是否已经存在这个skuId了
        // 我们需要先获取用户保存在SpringSecurity上下文中的信息,才能获取userId
        Long userId=getUserId();
        // 根据userId和cartDTO中的skuId进行用户购物车中商品的查询
        OmsCart omsCart =
                omsCartMapper.selectExistCart(userId, cartDTO.getSkuId());
        // 判断omsCart是否为null
        if(omsCart == null){
            // 如果omsCart是null,证明当前用户购物车中没有这个商品,执行新增操作
            // 要执行新增,需要将参数cartDTO中同名属性赋值到omsCart中
            // 但是omsCart是null会引发异常,所以要先实例化omsCart
            omsCart=new OmsCart();
            BeanUtils.copyProperties(cartDTO,omsCart);
            // 检查omsCart是否还有未赋值的必要属性,发送userId没有被赋值
            omsCart.setUserId(userId);
            // 执行新增操作
            omsCartMapper.saveCart(omsCart);
        }else{
            // 如果omsCart不是null,证明当前用户购物车中有这个商品,执行修改数量操作
            // 修改数量的具体方法如下:将当前omsCart对象的quantity和参数cartDTO的相加
            // 将相加的结果赋回到omsCart对象的quantity属性里
            omsCart.setQuantity(omsCart.getQuantity()+cartDTO.getQuantity());
            // 在执行数据库的修改,史omsCart新的quantity影响数据库
            omsCartMapper.updateQuantityById(omsCart);
        }
    }

    @Override
    public JsonPage<CartStandardVO> listCarts(Integer page, Integer pageSize) {
        // 先从SpringSecurity上下文中获取当前登录用户id
        Long userId=getUserId();
        // 在执行查询之前,先设置分页查询的条件
        PageHelper.startPage(page,pageSize);
        // 上面设置了分页条件,下面查询会生成limit关键字查询指定区域的数据
        List<CartStandardVO> list=omsCartMapper.selectCartByUserId(userId);
        // 返回JsonPage类型对象
        return JsonPage.restPage(new PageInfo<>(list));
    }

    // 支持批量删除购物车中商品的方法
    @Override
    public void removeCart(Long[] ids) {
        // 直接将ids参数赋值到mapper中
        int rows=omsCartMapper.deleteCartsByIds(ids);
        if(rows==0){
            throw new CoolSharkServiceException(
                    ResponseCode.NOT_FOUND,"您要删除的商品已经删除了!");
        }
    }

    // 清空购物车
    @Override
    public void removeAllCarts() {
        Long userId=getUserId();
        int rows=omsCartMapper.deleteCartsByUserId(userId);
        if(rows==0){
            throw new CoolSharkServiceException(
                    ResponseCode.NOT_FOUND,"您的购物车已经是空了!");
        }
    }

    // 根据userId和skuId删除购物车中商品
    @Override
    public void removeUserCarts(OmsCart omsCart) {
        // 购物车删除的效果无需验证,即使没有删除,也不要抛出异常,因为它不影响新增订单的流程
        omsCartMapper.deleteCartByUserIdAndSkuId(omsCart);
    }

    @Override
    public void updateQuantity(CartUpdateDTO cartUpdateDTO) {
        // 要执行修改数量,需要参数类型为OmsCart,所以先实例化它
        OmsCart omsCart=new OmsCart();
        // 当前方法参数CartUpdateDTO中包含了id和quantity属性,赋值给omsCart对象即可
        BeanUtils.copyProperties(cartUpdateDTO,omsCart);
        // 执行修改
        omsCartMapper.updateQuantityById(omsCart);
    }

    // 前端将JWT发送给服务器,服务器在运行控制器方法前先在过滤器中获取前端发送来的JWT
    // 过滤器中解析JWT,获取用户信息,保存在SpringSecurity上下文中
    // 下面的方法就是将用户信息从SpringSecurity上下文中获取
    public CsmallAuthenticationInfo getUserInfo(){
        // 获取SpringSecurity上下文对象
        UsernamePasswordAuthenticationToken token=
                (UsernamePasswordAuthenticationToken)
                        SecurityContextHolder.getContext().getAuthentication();
        // 为了程序逻辑严谨,判断一下token是否为null
        if(token == null){
            throw new CoolSharkServiceException(
                    ResponseCode.UNAUTHORIZED,"您还没有登录!");
        }
        // 从SpringSecurity上下文对象(token)中,获取用户信息
        CsmallAuthenticationInfo csmallAuthenticationInfo=
                (CsmallAuthenticationInfo) token.getCredentials();
        // 最终返回用户信息
        return csmallAuthenticationInfo;
    }

    // 业务逻辑层使用用户信息,只需要用户的id
    // 我们再编写一个方法,直接返回用户id,方便调用
    public Long getUserId(){
        return getUserInfo().getId();
    }



}

package com.cooxiao.mall.seckill.service.impl;

import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.order.service.IOmsOrderService;
import com.cooxiao.mall.pojo.order.dto.OrderAddDTO;
import com.cooxiao.mall.pojo.order.dto.OrderItemAddDTO;
import com.cooxiao.mall.pojo.order.vo.OrderAddVO;
import com.cooxiao.mall.pojo.seckill.dto.SeckillOrderAddDTO;
import com.cooxiao.mall.pojo.seckill.model.SeckillMessageRetry;
import com.cooxiao.mall.pojo.seckill.model.Success;
import com.cooxiao.mall.pojo.seckill.vo.SeckillCommitVO;
import com.cooxiao.mall.seckill.config.RabbitMqComponentConfiguration;
import com.cooxiao.mall.seckill.mapper.SeckillMessageRetryMapper;
import com.cooxiao.mall.seckill.service.ISeckillService;
import com.cooxiao.mall.seckill.utils.SeckillCacheUtils;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/10/7
 */
@Slf4j
@Service
public class SeckillServiceImpl implements ISeckillService {
    // 秒杀业务中,使用redis判断是否有库存,和用户是否重复购买
    // 这些操作都是操作数值,所以使用能够在Redis内部操作数值的对象
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    // 需要dubbo调用mall-order模块生成订单的方法
    @DubboReference
    private IOmsOrderService dubboOrderService;
    // 业务中需要使用RabbitMQ发送消息来实现记录秒杀成功信息
    @Autowired
    private RabbitTemplate rabbitTemplate;
    // MQ降级：本地消息表，MQ不可用时兜底
    @Autowired
    private SeckillMessageRetryMapper retryMapper;

    /*
    1.判断用户是否为重复购买和Redis中该Sku是否有库存
    2.秒杀订单转换成普通订单,需要使用dubbo调用order模块的生成订单方法
    3.使用消息队列(RabbitMQ)将秒杀成功记录信息保存到success表中
    4.秒杀订单信息返回
     */
    // 秒杀订单提交涉及跨服务Dubbo调用(order模块),需要Seata保证分布式事务一致性
    @GlobalTransactional
    @Override
    public SeckillCommitVO commitSeckill(SeckillOrderAddDTO seckillOrderAddDTO) {
        // 第一步:判断用户是否为重复购买和Redis中该Sku是否有库存
        // 从SpringSecurity上下文中获取userId
        Long userId=getUserId();
        // 从方法参数中订单项属性里获取skuId
        Long skuId=seckillOrderAddDTO.getSeckillOrderItemAddDTO().getSkuId();
        // 防重复提交：下单锁 setIfAbsent，1分钟自动过期，取消订单时由 order 模块主动删除
        // 注意：此锁 key 与支付后的购买标记 key（reseckill）分离，下单不影响 isPurchased
        String orderLockKey = SeckillCacheUtils.getOrderLockKey(skuId, userId);
        Boolean locked = stringRedisTemplate.boundValueOps(orderLockKey)
                .setIfAbsent("1", 1, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(locked)) {
            throw new CoolSharkServiceException(
                    ResponseCode.FORBIDDEN,"您已经购买过这个商品了,谢谢您的支持");
        }
        // 检查是否存在未支付的秒杀订单（锁过期后的二次下单拦截）
        String orderedKey = SeckillCacheUtils.getOrderedKey(skuId, userId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(orderedKey))) {
            stringRedisTemplate.delete(orderLockKey);
            throw new CoolSharkServiceException(
                    ResponseCode.FORBIDDEN, "您有一笔未支付的秒杀订单，请先支付或取消后再试");
        }
        // 程序运行到此处,表示用户确实是第一次购买该商品
        // 下面要判断这个商品是否还有库存
        // 根据要购买商品的skuId,从预热的库存数中,获取库存信息
        // mall:seckill:sku:stock:2
        String skuStockKey=SeckillCacheUtils.getStockKey(skuId);
        // 如果Redis中没有这个key,就要抛出异常
        if( ! stringRedisTemplate.hasKey(skuStockKey)){
            throw new CoolSharkServiceException(
                    ResponseCode.INTERNAL_SERVER_ERROR,
                    "没有该商品缓存信息(可能在真空期,等下一分钟再试)");
        }
        // 下面针对这个key进行库存的减少,使用和increment相反功能的decrement方法
        // 所以decrement方法的功能就是将当前库存数-1后返回
        Long leftStock=stringRedisTemplate
                .boundValueOps(skuStockKey).decrement();
        // leftStock是库存数-1之后,剩余的库存数
        // leftStock为0时,表示当前用户买到了最后一个库存,只有小于0时,才是库存不足
        if(leftStock<0){
            // 库存不足,要抛出异常终止程序,
            // 但是上面代码中已经记录了当前用户购买当前商品的次数,要恢复为0,才不影响用户下次购买
            stringRedisTemplate.delete(orderLockKey);
            throw new CoolSharkServiceException(
                    ResponseCode.BAD_REQUEST,"对不起,您要购买的商品已经售罄");
        }
        // 到此为止,当前用户通过了重复购买的检查,并且库存还有剩余,可以开始生成订单了!
        // 第二步: 秒杀订单转换成普通订单,需要使用dubbo调用order模块的生成订单方法
        OrderAddDTO orderAddDTO=
                convertSeckillOrderToOrder(seckillOrderAddDTO);
        // 经过转换得到了普通订单对象orderAddDTO,但是还没有给userId赋值
        orderAddDTO.setUserId(userId);
        // dubbo调用生成订单的方法 + MQ发送（统一try-catch保护，MQ不可用时补偿Redis）
        OrderAddVO orderAddVO;
        try {
            orderAddVO = dubboOrderService.addOrder(orderAddDTO);
            // 标记"已下单未支付"，防止锁过期后重复下单，支付/取消后由 order 模块清理
            stringRedisTemplate.boundValueOps(orderedKey).set(orderAddVO.getSn(), 2, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("Dubbo创建订单失败, skuId={}, userId={}, 补偿Redis库存", skuId, userId, e);
            stringRedisTemplate.boundValueOps(skuStockKey).increment();
            stringRedisTemplate.delete(orderLockKey);
            throw new CoolSharkServiceException(
                    ResponseCode.INTERNAL_SERVER_ERROR, "抢购人数过多，请稍后再试");
        }

        // 第三步: 消息表 + MQ（统一try-catch，失败时补偿Redis库存）
        try {
            Success success = new Success();
            success.setId(IdWorker.getId());
            BeanUtils.copyProperties(
                    seckillOrderAddDTO.getSeckillOrderItemAddDTO(), success);
            success.setUserId(userId);
            success.setSeckillPrice(
                    seckillOrderAddDTO.getSeckillOrderItemAddDTO().getPrice());
            success.setOrderSn(orderAddVO.getSn());

            SeckillMessageRetry retry = new SeckillMessageRetry();
            retry.setId(IdWorker.getId());
            retry.setUserId(userId);
            retry.setSkuId(skuId);
            retry.setOrderSn(orderAddVO.getSn());
            retry.setMessageBody(JSON.toJSONString(success));
            retry.setStatus(0);
            retry.setRetryCount(0);
            retry.setGmtCreate(java.time.LocalDateTime.now());
            retryMapper.insert(retry);

            rabbitTemplate.convertAndSend(
                    RabbitMqComponentConfiguration.SECKILL_EX,
                    RabbitMqComponentConfiguration.SECKILL_RK,
                    success);
            retryMapper.updateStatusSent(retry.getId());
        } catch (Exception e) {
            log.error("秒杀后处理失败, skuId={}, userId={}, exceptionType={}, exceptionMsg={}, 补偿Redis库存",
                    skuId, userId, e.getClass().getName(), e.getMessage(), e);
            stringRedisTemplate.boundValueOps(skuStockKey).increment();
            stringRedisTemplate.delete(orderLockKey);
            stringRedisTemplate.delete(orderedKey);
            throw new CoolSharkServiceException(
                    ResponseCode.INTERNAL_SERVER_ERROR, "抢购人数过多，请稍后再试");
        }
        // 消息已发出/已补偿,本方法无需考虑消息接收的问题
        // 第四步: 秒杀订单信息返回
        // 当前方法要求返回值类型为SeckillCommitVO
        // 经观察,属性和OrderAddVO完全一致,直接同名属性赋值
        SeckillCommitVO commitVO=new SeckillCommitVO();
        BeanUtils.copyProperties(orderAddVO,commitVO);
        // 最后别忘了返回!!!!
        return commitVO;
    }
    // 将秒杀订单转换为普通订单的方法
    private OrderAddDTO convertSeckillOrderToOrder(SeckillOrderAddDTO seckillOrderAddDTO) {
        // 实例化普通订单对象
        OrderAddDTO orderAddDTO=new OrderAddDTO();
        // 秒杀订单中包含除订单项之外的所有信息,直接同名属性赋值
        BeanUtils.copyProperties(seckillOrderAddDTO,orderAddDTO);
        // 赋值之后就剩下订单项没有值了
        // orderAddDTO的订单项是OrderItemAddDTO的集合
        // seckillOrderAddDTO的订单项是SeckillOrderItemAddDTO的对象
        // 所以我们先要将SeckillOrderItemAddDTO转换为OrderItemAddDTO
        // 先实例化普通订单项
        OrderItemAddDTO orderItemAddDTO=new OrderItemAddDTO();
        // 经过观察秒杀订单项也是包含的所有普通订单项需要的信息,直接赋值
        BeanUtils.copyProperties(
                seckillOrderAddDTO.getSeckillOrderItemAddDTO(),
                orderItemAddDTO);
        // 现在我们普通订单和普通订单项都有了正确的属性值
        // 下面要将订单项新增到普通订单的订单项集合中
        // 先实例化订单项集合对象
        List<OrderItemAddDTO> list=new ArrayList<>();
        // 将转换好的订单项添加到这个集合中
        list.add(orderItemAddDTO);
        // 将list赋值到普通订单对象的订单项集合中
        orderAddDTO.setOrderItems(list);
        // 转换完成!返回赋好值的订单对象
        return orderAddDTO;
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

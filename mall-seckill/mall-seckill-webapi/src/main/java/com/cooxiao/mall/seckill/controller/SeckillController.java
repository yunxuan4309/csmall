package com.cooxiao.mall.seckill.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.seckill.dto.SeckillOrderAddDTO;
import com.cooxiao.mall.pojo.seckill.vo.SeckillCommitVO;
import com.cooxiao.mall.seckill.exception.SeckillBlockHandler;
import com.cooxiao.mall.seckill.exception.SeckillFallback;
import com.cooxiao.mall.seckill.service.ISeckillService;
import com.cooxiao.mall.seckill.utils.SeckillCacheUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/10/7
 */
@RestController
@RequestMapping("/seckill")
@Api(tags = "执行秒杀模块")
public class SeckillController {
    @Autowired
    private ISeckillService seckillService;
    @Autowired
    private RedisTemplate redisTemplate;

    @PostMapping("/{randCode}")
    @ApiOperation("验证随机码并提交秒杀订单")
    @ApiImplicitParam(value = "随机码",name="randCode",required = true)
    @PreAuthorize("hasRole('user')")
    @SentinelResource(value = "秒杀订单提交",
            blockHandlerClass = SeckillBlockHandler.class,blockHandler = "seckillBlock",
            fallbackClass = SeckillFallback.class,fallback = "seckillFallback")
    public JsonResult<SeckillCommitVO> commitSeckill(
            @PathVariable String randCode,
            @Validated SeckillOrderAddDTO seckillOrderAddDTO){
        // 先获取商品的spuId
        Long spuId=seckillOrderAddDTO.getSpuId();
        // 确定这个spuId对相应随机码的key
        String randCodeKey= SeckillCacheUtils.getRandCodeKey(spuId);
        // 判断Redis中是否包含这个Key
        if (redisTemplate.hasKey(randCodeKey)){
            // 如果Redis中有这个key,就要从Redis取出这个值,和randCode参数对比
            String redisRandCode=redisTemplate
                    .boundValueOps(randCodeKey).get()+"";
            // 判断redisRandCode和参数randCode是否一致
            if( ! redisRandCode.equals(randCode)){
                // 若不一致,抛出异常
                throw new CoolSharkServiceException(
                        ResponseCode.NOT_FOUND,"随机码不正确(实际开发不要给这样的提示)");
            }
            // 程序运行到这,表示两个随机码相同,调用业务层提交秒杀订单的方法
            SeckillCommitVO commitVO =
                    seckillService.commitSeckill(seckillOrderAddDTO);
            // 运行完成,返回给前端
            return JsonResult.ok(commitVO);

        }else{
            // Redis中没有这个key,直接抛异常
            throw new CoolSharkServiceException(
                    ResponseCode.NOT_FOUND,"没有找到当前商品的随机码(等下一分钟再试)");
        }
    }
}

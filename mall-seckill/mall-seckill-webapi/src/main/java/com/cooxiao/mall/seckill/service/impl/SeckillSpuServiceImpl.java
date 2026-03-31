package com.cooxiao.mall.seckill.service.impl;

import java.util.concurrent.ThreadLocalRandom;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.product.vo.SpuDetailStandardVO;
import com.cooxiao.mall.pojo.product.vo.SpuStandardVO;
import com.cooxiao.mall.pojo.seckill.model.SeckillSpu;
import com.cooxiao.mall.pojo.seckill.vo.SeckillSpuDetailSimpleVO;
import com.cooxiao.mall.pojo.seckill.vo.SeckillSpuVO;
import com.cooxiao.mall.product.service.seckill.IForSeckillSpuService;
import com.cooxiao.mall.seckill.mapper.SeckillSpuMapper;
import com.cooxiao.mall.seckill.service.ISeckillSpuService;
import com.cooxiao.mall.seckill.utils.SeckillCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/29
 */
@Service
@Slf4j
public class SeckillSpuServiceImpl implements ISeckillSpuService {

    // 装配查询全部秒杀商品信息的mapper
    @Autowired
    private SeckillSpuMapper seckillSpuMapper;

    // 查询spu常规信息,需要dubbo调用product模块提供的方法
    @DubboReference
    private IForSeckillSpuService dubboSeckillSpuService;
    // 分页查询秒杀商品信息
    // 返回值是一个SeckillSpuVO类型,它既包含spu常规信息又包含spu秒杀信息
    // 秒杀信息从mall_seckill数据库获取,常规信息从mall_pms获取(dubbo调用)
    @Override
    public JsonPage<SeckillSpuVO> listSeckillSpus(Integer page, Integer pageSize) {
        // 设置分页条件
        Page<SeckillSpu> pageParam = new Page<>(page, pageSize);
        // 执行查询秒杀商品列表的方法
        IPage<SeckillSpu> seckillSpuPage = seckillSpuMapper.findSeckillSpus(pageParam);
        // 实例化一个SeckillSpuVO泛型的集合,用于最终返回
        // SeckillSpuVO既包含spu常规信息又包含spu秒杀信息
        List<SeckillSpuVO> seckillSpuVOs = new ArrayList<>();
        // 遍历seckillSpus(从数据库查询出的只有秒杀信息的集合)
        for (SeckillSpu seckillSpu : seckillSpuPage.getRecords()) {
            // 获取 spuId
            Long spuId = seckillSpu.getSpuId();
            // 根据 spuId 利用 Dubbo 查询到该商品的常规信息
            SpuStandardVO standardVO = dubboSeckillSpuService.getSpuById(spuId);
            // 实例化 SeckillSpuVO 对象
            SeckillSpuVO seckillSpuVO = new SeckillSpuVO();
            // 将常规信息同名属性赋值到 seckillSpuVO
            BeanUtils.copyProperties(standardVO, seckillSpuVO);
            // 常规信息赋值完毕后,将秒杀信息手动赋值到 seckillSpuVO
            seckillSpuVO.setSeckillListPrice(seckillSpu.getListPrice());
            seckillSpuVO.setStartTime(seckillSpu.getStartTime());
            seckillSpuVO.setEndTime(seckillSpu.getEndTime());
            // 到此为止, seckillSpuVO 就赋值了常规信息和秒杀信息
            seckillSpuVOs.add(seckillSpuVO);
        }
        // 构建分页结果
        Page<SeckillSpuVO> resultPage = new Page<>(seckillSpuPage.getCurrent(), seckillSpuPage.getSize(), seckillSpuPage.getTotal());
        resultPage.setRecords(seckillSpuVOs);
        // 最后返回分页类型的返回值
        return JsonPage.restPage(resultPage);
    }
    // 装配操作Redis的对象
    @Autowired
    private RedisTemplate redisTemplate;
    // SeckillSpuVO既包含秒杀信息又包含常规信息
    @Override
    public SeckillSpuVO getSeckillSpu(Long spuId) {
        // 完整的代码中,这里应该先从Redis中获取布隆过滤器
        // 使用布隆过滤器判断参数spuId是否在数据库中存在,如果不存在直接抛出异常


        // 当前业务要返回SeckillSpuVO,是要保存到Redis中的,先获取这个对象的key
        // spuVOKey:mall:seckill:spu:vo:2
        String spuVOKey= SeckillCacheUtils.getSeckillSpuVOKey(spuId);
        // 声明一个返回值类型的对象,方便后续操作
        SeckillSpuVO seckillSpuVO=null;
        // 判断Redis中是否包含这个key
        if (redisTemplate.hasKey(spuVOKey)){
            seckillSpuVO= (SeckillSpuVO)
                    redisTemplate.boundValueOps(spuVOKey).get();
        }else{
            // 如果redis中没有这个key,要从数据库查询
            // 要查询秒杀信息和常规信息最后都赋值到seckillSpuVO对象中
            SeckillSpu seckillSpu=seckillSpuMapper.findSeckillSpuById(spuId);
            // 判断一下这个seckillSpu是否为null(因为布隆过滤器可能会误判)
            if(seckillSpu == null){
                // 进入这个if就证明上面发生了缓存穿透
                throw new CoolSharkServiceException(
                        ResponseCode.NOT_FOUND,"您要访问的商品不存在");
            }
            // 查询 spu常规信息
            SpuStandardVO standardVO =
                    dubboSeckillSpuService.getSpuById(spuId);
            // 将常规信息和秒杀信息都赋值到seckillSpuVO对象中
            seckillSpuVO=new SeckillSpuVO();
            BeanUtils.copyProperties(standardVO,seckillSpuVO);
            // 收到赋值秒杀信息
            seckillSpuVO.setSeckillListPrice(seckillSpu.getListPrice());
            seckillSpuVO.setStartTime(seckillSpu.getStartTime());
            seckillSpuVO.setEndTime(seckillSpu.getEndTime());
            // 将seckillSpuVO对象保存到Redis中,方便后面的请求从Redis中获取
            redisTemplate.boundValueOps(spuVOKey).set(
                    seckillSpuVO,
                    1000*60*5 + ThreadLocalRandom.current().nextInt(10000),
                    TimeUnit.MILLISECONDS);
        }
        // 到此为止,seckillSpuVO对象只有url属性没有赋值了
        // url属性是否有值,直接影响前端页面是否能够提交订单
        // 给url赋值的条件是,当前时间是否允许购买当前商品
        // 获取当前时间
        LocalDateTime nowTime=LocalDateTime.now();
        // 判断使用java代码,尽量不连接数据库,节省系统资源
        // 判断逻辑是秒杀开始时间小于当前时间,并且当前时间小于秒杀结束时间
        if(seckillSpuVO.getStartTime().isBefore(nowTime) &&
                nowTime.isBefore(seckillSpuVO.getEndTime())){
            // 进入当前if表示现在允许秒杀购买本spu商品,要为url赋值
            // 我们要从Redis中获取已经预热的随机码
            String randCodeKey=SeckillCacheUtils.getRandCodeKey(spuId);
            // 判断Redis中有没有这个key,如果没有抛出异常
            if( ! redisTemplate.hasKey(randCodeKey)){
                // 异常提示随机码不存在
                throw new CoolSharkServiceException(
                        ResponseCode.INTERNAL_SERVER_ERROR,"随机码不存在(等到下一分钟再试)");
            }
            // key正常,从Redis中获取
            int randCode= (int) redisTemplate.boundValueOps(randCodeKey).get();
            // 随机码赋值到url属性中
            seckillSpuVO.setUrl("/seckill/"+randCode);
            log.info("商品详情对象构建完成,url属性为:{}",seckillSpuVO.getUrl());
        }
        // 最后别忘了把seckillSpuVO返回!!!!!!
        return seckillSpuVO;
    }

    // 项目中没有给定SpuDetail用的key常量,所以我们自己声明一个
    public static final String
            SECKILL_SPU_DETAIL_PREFIX="seckill:spu:detail:";
    // 根据 spuId 查询秒杀用 spuDetail 信息
    @Override
    public SeckillSpuDetailSimpleVO getSeckillSpuDetail(Long spuId) {
        // 先获取当前 spu 对应的 key
        String spuDetailKey=SECKILL_SPU_DETAIL_PREFIX+spuId;
        // 声明返回值类型对象
        SeckillSpuDetailSimpleVO simpleVO=null;
        // 判断这个 Key 是否存在
        if(redisTemplate.hasKey(spuDetailKey)){
            // 如果存在,从 Redis 中获取
            simpleVO= (SeckillSpuDetailSimpleVO)
                    redisTemplate.boundValueOps(spuDetailKey).get();
        }else{
            // 如果不存在这个 Key
            // dubbo 调用 product 模块查询 spuDetail 对象
            SpuDetailStandardVO spuDetailStandardVO =
                    dubboSeckillSpuService.getSpuDetailById(spuId);
            // 先实例化 simpleVO,再给它赋值同名属性
            simpleVO=new SeckillSpuDetailSimpleVO();
            BeanUtils.copyProperties(spuDetailStandardVO,simpleVO);
            // 保存到 Redis 中
            redisTemplate.boundValueOps(spuDetailKey).set(
                    simpleVO,
                    1000*60*5+ThreadLocalRandom.current().nextInt(10000),
                    TimeUnit.MILLISECONDS);
        }
        // 最后别忘了返回 !!!!
        return simpleVO;
    }
}

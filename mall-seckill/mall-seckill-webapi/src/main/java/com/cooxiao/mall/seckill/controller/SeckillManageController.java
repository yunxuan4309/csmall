package com.cooxiao.mall.seckill.controller;

import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.pojo.seckill.dto.SeckillSkuAddDTO;
import com.cooxiao.mall.pojo.seckill.dto.SeckillSpuAddDTO;
import com.cooxiao.mall.pojo.seckill.model.SeckillSku;
import com.cooxiao.mall.pojo.seckill.model.SeckillSpu;
import com.cooxiao.mall.seckill.mapper.SeckillSkuMapper;
import com.cooxiao.mall.seckill.mapper.SeckillSpuMapper;
import com.cooxiao.mall.seckill.utils.SeckillCacheUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀商品管理控制器（管理员使用）
 */
@RestController
@RequestMapping("/seckill/manage")
@Api(tags = "秒杀商品管理")
@Slf4j
public class SeckillManageController {

    @Autowired
    private SeckillSpuMapper seckillSpuMapper;
    @Autowired
    private SeckillSkuMapper seckillSkuMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    @PostMapping("/spu")
    @ApiOperation("新增秒杀SPU")
    @PreAuthorize("isAuthenticated()")
    public JsonResult<String> addSeckillSpu(@Validated @RequestBody SeckillSpuAddDTO seckillSpuAddDTO) {
        SeckillSpu seckillSpu = new SeckillSpu();
        BeanUtils.copyProperties(seckillSpuAddDTO, seckillSpu);
        seckillSpuMapper.insert(seckillSpu);
        // 时间窗口/秒杀价已变，失效该 SPU 的缓存 VO，防止详情页读到旧窗口误显示"已结束"
        evictSeckillSpuVoCache(seckillSpuAddDTO.getSpuId());
        return JsonResult.ok("新增秒杀SPU成功");
    }

    @DeleteMapping("/spu/{id}")
    @ApiOperation("删除秒杀SPU")
    @PreAuthorize("isAuthenticated()")
    public JsonResult<String> deleteSeckillSpu(@PathVariable Long id) {
        // 删除前先取出该 SPU 对应的 pms spuId，用于失效缓存
        SeckillSpu seckillSpu = seckillSpuMapper.selectById(id);
        seckillSpuMapper.deleteById(id);
        if (seckillSpu != null && seckillSpu.getSpuId() != null) {
            evictSeckillSpuVoCache(seckillSpu.getSpuId());
        }
        return JsonResult.ok("删除秒杀SPU成功");
    }

    @PostMapping("/sku")
    @ApiOperation("新增秒杀SKU")
    @PreAuthorize("isAuthenticated()")
    public JsonResult<String> addSeckillSku(@Validated @RequestBody SeckillSkuAddDTO seckillSkuAddDTO) {
        SeckillSku seckillSku = new SeckillSku();
        BeanUtils.copyProperties(seckillSkuAddDTO, seckillSku);
        if (seckillSku.getSeckillLimit() == null) {
            seckillSku.setSeckillLimit(1);
        }
        seckillSkuMapper.insert(seckillSku);
        return JsonResult.ok("新增秒杀SKU成功");
    }

    @DeleteMapping("/sku/{id}")
    @ApiOperation("删除秒杀SKU")
    @PreAuthorize("isAuthenticated()")
    public JsonResult<String> deleteSeckillSku(@PathVariable Long id) {
        seckillSkuMapper.deleteById(id);
        return JsonResult.ok("删除秒杀SKU成功");
    }

    /**
     * 失效指定秒杀 SPU 的缓存 VO（key = mall:seckill:spu:vo:{pmsSpuId}）。
     * 修改秒杀时间窗口/秒杀价后不失效该缓存，会让详情页在缓存 TTL（约 2h）内
     * 仍读到旧窗口，从而把进行中的秒杀误显示为"已结束"。详见 TODO #14 备注。
     */
    private void evictSeckillSpuVoCache(Long pmsSpuId) {
        if (pmsSpuId == null) {
            return;
        }
        String voKey = SeckillCacheUtils.getSeckillSpuVOKey(pmsSpuId);
        try {
            Boolean deleted = redisTemplate.delete(voKey);
            log.info("秒杀SPU缓存失效: key={}, deleted={}", voKey, deleted);
        } catch (Exception e) {
            // 缓存失效失败不应阻断DB写入，仅告警
            log.warn("秒杀SPU缓存失效失败，key={}, 原因: {}", voKey, e.getMessage(), e);
        }
    }
}

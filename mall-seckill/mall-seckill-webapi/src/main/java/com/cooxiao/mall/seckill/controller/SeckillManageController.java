package com.cooxiao.mall.seckill.controller;

import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.pojo.seckill.dto.SeckillSkuAddDTO;
import com.cooxiao.mall.pojo.seckill.dto.SeckillSpuAddDTO;
import com.cooxiao.mall.pojo.seckill.model.SeckillSku;
import com.cooxiao.mall.pojo.seckill.model.SeckillSpu;
import com.cooxiao.mall.seckill.mapper.SeckillSkuMapper;
import com.cooxiao.mall.seckill.mapper.SeckillSpuMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀商品管理控制器（管理员使用）
 */
@RestController
@RequestMapping("/seckill/manage")
@Api(tags = "秒杀商品管理")
public class SeckillManageController {

    @Autowired
    private SeckillSpuMapper seckillSpuMapper;
    @Autowired
    private SeckillSkuMapper seckillSkuMapper;

    @PostMapping("/spu")
    @ApiOperation("新增秒杀SPU")
    @PreAuthorize("hasRole('admin')")
    public JsonResult<String> addSeckillSpu(@Validated @RequestBody SeckillSpuAddDTO seckillSpuAddDTO) {
        SeckillSpu seckillSpu = new SeckillSpu();
        BeanUtils.copyProperties(seckillSpuAddDTO, seckillSpu);
        seckillSpuMapper.insert(seckillSpu);
        return JsonResult.ok("新增秒杀SPU成功");
    }

    @DeleteMapping("/spu/{id}")
    @ApiOperation("删除秒杀SPU")
    @PreAuthorize("hasRole('admin')")
    public JsonResult<String> deleteSeckillSpu(@PathVariable Long id) {
        seckillSpuMapper.deleteById(id);
        return JsonResult.ok("删除秒杀SPU成功");
    }

    @PostMapping("/sku")
    @ApiOperation("新增秒杀SKU")
    @PreAuthorize("hasRole('admin')")
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
    @PreAuthorize("hasRole('admin')")
    public JsonResult<String> deleteSeckillSku(@PathVariable Long id) {
        seckillSkuMapper.deleteById(id);
        return JsonResult.ok("删除秒杀SKU成功");
    }
}

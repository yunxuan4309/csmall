package com.cooxiao.mall.seckill.controller;

import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.pojo.seckill.vo.SeckillSkuVO;
import com.cooxiao.mall.seckill.service.ISeckillSkuService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/29
 */
@RestController
@RequestMapping("/seckill/sku")
@Api(tags = "秒杀sku模块")
public class SeckillSkuController {
    @Autowired
    private ISeckillSkuService seckillSkuService;

    @GetMapping("/list/{spuId}")
    @ApiOperation("根据spuId查询sku列表")
    @ApiImplicitParam(value = "spuId",name = "spuId",example = "2")
    public JsonResult<List<SeckillSkuVO>> listSeckillSkus(
            @PathVariable Long spuId){
        List<SeckillSkuVO> seckillSkuVOs = seckillSkuService.
                listSeckillSkus(spuId);
        return JsonResult.ok(seckillSkuVOs);
    }
}

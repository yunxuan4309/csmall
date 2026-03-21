package com.cooxiao.mall.front.service.impl;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.front.service.IFrontProductService;
import com.cooxiao.mall.product.service.front.IForFrontAttributeService;
import com.cooxiao.mall.product.service.front.IForFrontSkuService;
import com.cooxiao.mall.product.service.front.IForFrontSpuService;
import com.cooxiao.mall.pojo.product.vo.*;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FrontProductServiceImpl implements IFrontProductService {
    @DubboReference
    private IForFrontSpuService dubboSpuService;

    // 根据spuId查询sku列表用的对象
    @DubboReference
    private IForFrontSkuService dubboSkuService;

    // 根据spuId查询所有规格Attribute的对象
    @DubboReference
    private IForFrontAttributeService dubboAttributeService;

    //根据分类id分页查询spu列表
    @Override
    public JsonPage<SpuListItemVO> listSpuByCategoryId(Long categoryId, Integer page, Integer pageSize) {
        // 这里dubbo调用的方法是已经进行了分页逻辑的
        // 所以获取的直接是jsonPage类型对象,我们只需要调用然后返回即可
        JsonPage<SpuListItemVO> jsonPage =
                dubboSpuService.listSpuByCategoryId(categoryId, page, pageSize);
        // 别忘了返回jsonPage!!!!!
        return jsonPage;
    }

    //根据spuId查询spu商品详情
    @Override
    public SpuStandardVO getFrontSpuById(Long id) {
        SpuStandardVO spuStandardVO=dubboSpuService.getSpuById(id);
        return spuStandardVO;
    }

    // 根据spuId查询当前商品包含的sku列表
    @Override
    public List<SkuStandardVO> getFrontSkusBySpuId(Long spuId) {
        List<SkuStandardVO> list=dubboSkuService.getSkusBySpuId(spuId);
        return list;
    }

    // 根据spuId查询当前商品详情(展示图片)
    @Override
    public SpuDetailStandardVO getSpuDetail(Long spuId) {
        SpuDetailStandardVO spuDetailStandardVO=
                dubboSpuService.getSpuDetailById(spuId);
        return spuDetailStandardVO;
    }

    // 根据spuId查询当前商品所有规格选项
    @Override
    public List<AttributeStandardVO> getSpuAttributesBySpuId(Long spuId) {
        List<AttributeStandardVO> list=
                dubboAttributeService.getSpuAttributesBySpuId(spuId);
        return list;
    }
}

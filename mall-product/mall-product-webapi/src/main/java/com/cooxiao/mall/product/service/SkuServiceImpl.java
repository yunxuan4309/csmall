package com.cooxiao.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.product.constant.DataCommonConst;
import com.cooxiao.mall.product.mapper.SkuMapper;
import com.cooxiao.mall.product.mapper.SkuSpecificationMapper;
import com.cooxiao.mall.pojo.product.dto.SkuAddNewDTO;
import com.cooxiao.mall.pojo.product.dto.SkuUpdateFullInfoDTO;
import com.cooxiao.mall.pojo.product.model.Sku;
import com.cooxiao.mall.pojo.product.model.SkuSpecification;
import com.cooxiao.mall.pojo.product.vo.SkuStandardVO;
import com.cooxiao.mall.product.utils.IdGeneratorUtils;
import com.cooxiao.mall.product.utils.ListConvertUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>SKU（Stock Keeping Unit）业务实现类</p>
 *
 * @author cooxiao.com QQ:25380243
 * @since 2021-11-30
 */
@Service
@Slf4j
public class SkuServiceImpl implements ISkuService {

    @Autowired
    private SkuMapper skuMapper;
    @Autowired
    private SkuSpecificationMapper skuSpecificationMapper;

    @Override
    public void addNew(SkuAddNewDTO skuAddNewDTO) {
        log.debug("skuAddNewDTO = {}", skuAddNewDTO);
        Long skuId = IdGeneratorUtils.getDistributeId(IdGeneratorUtils.Key.SKU);

        Sku sku = new Sku();
        BeanUtils.copyProperties(skuAddNewDTO, sku);
        sku.setId(skuId);
        sku.setSort(skuAddNewDTO.getSort() == null ? DataCommonConst.SORT_DEFAULT : skuAddNewDTO.getSort());
        skuMapper.insert(sku);

        String specifications = skuAddNewDTO.getSpecifications();
        log.debug("specifications = {}", specifications);
        List<SkuSpecification> skuSpecificationList = ListConvertUtils.stringToList(specifications, SkuSpecification.class);
        if (skuSpecificationList != null) {
            for (SkuSpecification skuSpecification : skuSpecificationList) {
                log.debug("skuSpecification = {}", skuSpecification);
                skuSpecification.setSkuId(skuId);
            }
        }
        skuSpecificationMapper.insertBatch(skuSpecificationList);
    }

    @Override
    public void  updateFullInfoById(Long id, SkuUpdateFullInfoDTO skuUpdateFullInfoDTO) {
        Object checkExistQueryResult = skuMapper.getById(id);
        if (checkExistQueryResult == null) {
            throw new CoolSharkServiceException(ResponseCode.NOT_FOUND, "更新SKU失败，尝试访问的数据不存在！");
        }

        Sku sku = new Sku();
        sku.setId(id);
        BeanUtils.copyProperties(skuUpdateFullInfoDTO, sku);
        int rows = skuMapper.updateFullInfoById(sku);
        if (rows != 1) {
            throw new CoolSharkServiceException(ResponseCode.INTERNAL_SERVER_ERROR, "更新SKU失败，服务器忙，请稍后再次尝试！");
        }
    }

    @Override
    public SkuStandardVO getById(Long id) {
        SkuStandardVO sku = skuMapper.getById(id);
        if (sku == null) {
            throw new CoolSharkServiceException(ResponseCode.NOT_FOUND, "获取SKU详情失败，尝试访问的数据不存在！");
        }
        return sku;
    }

    @Override
    public JsonPage<SkuStandardVO> list(Long spuId, Integer pageNum, Integer pageSize) {
        Page<Sku> pageParam = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Sku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sku::getSpuId, spuId)
               .orderByDesc(Sku::getSort, Sku::getGmtCreate);
        IPage<Sku> result = skuMapper.selectPage(pageParam, wrapper);
        // 转换为VO
        List<SkuStandardVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        IPage<SkuStandardVO> pageVO = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageVO.setRecords(voList);
        return JsonPage.restPage(pageVO);
    }

    private SkuStandardVO convertToVO(Sku sku) {
        if (sku == null) {
            return null;
        }
        SkuStandardVO vo = new SkuStandardVO();
        BeanUtils.copyProperties(sku, vo);
        return vo;
    }

}

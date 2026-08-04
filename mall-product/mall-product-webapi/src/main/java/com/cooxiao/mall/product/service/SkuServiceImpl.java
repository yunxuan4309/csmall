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
import com.cooxiao.mall.product.mapper.SpuMapper;
import com.cooxiao.mall.pojo.product.dto.SkuAddNewDTO;
import com.cooxiao.mall.pojo.product.dto.SkuGenerateDTO;
import com.cooxiao.mall.pojo.product.dto.SkuUpdateFullInfoDTO;
import com.cooxiao.mall.pojo.product.model.Sku;
import com.cooxiao.mall.pojo.product.model.SkuSpecification;
import com.cooxiao.mall.pojo.product.model.Spu;
import com.cooxiao.mall.pojo.product.vo.SkuStandardVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.cooxiao.mall.product.utils.ListConvertUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    @Autowired
    private SpuMapper spuMapper;

    @Override
    public void addNew(SkuAddNewDTO skuAddNewDTO) {
        log.debug("skuAddNewDTO = {}", skuAddNewDTO);
        Long skuId = IdWorker.getId();

        Sku sku = new Sku();
        BeanUtils.copyProperties(skuAddNewDTO, sku);
        sku.setId(skuId);
        sku.setSort(skuAddNewDTO.getSort() == null ? DataCommonConst.SORT_DEFAULT : skuAddNewDTO.getSort());
        skuMapper.insert(sku);

        String specifications = skuAddNewDTO.getSpecifications();
        log.debug("specifications = {}", specifications);
        if (specifications == null || specifications.trim().isEmpty()) {
            // 无规格时跳过规格明细写入（避免 insertBatch(null) NPE）
            return;
        }
        List<SkuSpecification> skuSpecificationList = ListConvertUtils.stringToList(specifications, SkuSpecification.class);
        if (skuSpecificationList == null || skuSpecificationList.isEmpty()) {
            return;
        }
        for (SkuSpecification skuSpecification : skuSpecificationList) {
            log.debug("skuSpecification = {}", skuSpecification);
            skuSpecification.setSkuId(skuId);
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

    @Override
    public void deleteById(Long id) {
        // 校验 SKU 存在
        SkuStandardVO exist = skuMapper.getById(id);
        if (exist == null) {
            throw new CoolSharkServiceException(ResponseCode.NOT_FOUND, "删除SKU失败，尝试访问的SKU不存在！");
        }
        // 删除该 SKU 的规格明细
        List<Long> skuIds = new ArrayList<>();
        skuIds.add(id);
        skuSpecificationMapper.deleteBySkuIds(skuIds);
        // 删除 SKU
        int rows = skuMapper.deleteById(id);
        if (rows != 1) {
            throw new CoolSharkServiceException(ResponseCode.INTERNAL_SERVER_ERROR, "删除SKU失败，服务器忙，请稍后再次尝试！");
        }
    }

    @Override
    public int generateSkus(SkuGenerateDTO skuGenerateDTO) {
        Long spuId = skuGenerateDTO.getSpuId();
        // 校验 SPU 存在
        Spu spu = spuMapper.selectById(spuId);
        if (spu == null) {
            throw new CoolSharkServiceException(ResponseCode.NOT_FOUND, "生成SKU失败，SPU不存在！");
        }
        List<SkuGenerateDTO.SelectedAttribute> selected = skuGenerateDTO.getAttributes();
        if (selected == null || selected.isEmpty()) {
            throw new CoolSharkServiceException(ResponseCode.BAD_REQUEST, "生成SKU失败，请选择至少一个属性！");
        }

        // 提取每个属性的值列表，做笛卡尔积
        List<List<String[]>> attrValues = new ArrayList<>();
        for (SkuGenerateDTO.SelectedAttribute attr : selected) {
            List<String[]> valueRows = new ArrayList<>();
            for (String value : attr.getValues()) {
                // 每行 = [attributeId, attributeName, value]
                valueRows.add(new String[]{String.valueOf(attr.getAttributeId()), attr.getAttributeName(), value});
            }
            attrValues.add(valueRows);
        }
        List<List<String[]>> cartesian = cartesianProduct(attrValues);

        // SPU 的 title 作为 SKU 标题前缀
        String spuTitle = spu.getTitle() == null ? spu.getName() : spu.getTitle();

        // 批量生成 SKU
        List<Sku> skuList = new ArrayList<>();
        List<SkuSpecification> specList = new ArrayList<>();
        for (List<String[]> combo : cartesian) {
            Long skuId = IdWorker.getId();
            // 标题 = SPU标题 + 各属性值
            String titleSuffix = combo.stream()
                    .map(row -> row[2])
                    .collect(Collectors.joining(" "));
            String skuTitle = spuTitle + " " + titleSuffix;

            Sku sku = new Sku();
            sku.setId(skuId);
            sku.setSpuId(spuId);
            sku.setTitle(skuTitle.trim());
            sku.setAttributeTemplateId(spu.getAttributeTemplateId());
            sku.setPrice(spu.getListPrice() == null ? BigDecimal.ZERO : spu.getListPrice());
            sku.setStock(0);
            sku.setStockThreshold(0);
            sku.setSort(DataCommonConst.SORT_DEFAULT);
            skuList.add(sku);

            // 规格明细
            int sort = 0;
            for (String[] row : combo) {
                SkuSpecification spec = new SkuSpecification();
                spec.setId(IdWorker.getId());
                spec.setSkuId(skuId);
                spec.setAttributeId(Long.parseLong(row[0]));
                spec.setAttributeName(row[1]);
                spec.setAttributeValue(row[2]);
                spec.setSort(sort++);
                specList.add(spec);
            }
        }

        if (!skuList.isEmpty()) {
            skuMapper.insertBatch(skuList);
            if (!specList.isEmpty()) {
                skuSpecificationMapper.insertBatch(specList);
            }
        }
        return skuList.size();
    }

    /**
     * 多属性值列表的笛卡尔积
     */
    private List<List<String[]>> cartesianProduct(List<List<String[]>> lists) {
        List<List<String[]>> result = new ArrayList<>();
        if (lists == null || lists.isEmpty()) {
            return result;
        }
        cartesianRecursive(lists, 0, new ArrayList<>(), result);
        return result;
    }

    private void cartesianRecursive(List<List<String[]>> lists, int depth, List<String[]> current, List<List<String[]>> result) {
        if (depth == lists.size()) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (String[] row : lists.get(depth)) {
            current.add(row);
            cartesianRecursive(lists, depth + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

}

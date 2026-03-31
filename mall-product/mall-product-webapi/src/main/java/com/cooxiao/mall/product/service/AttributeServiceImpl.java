package com.cooxiao.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.product.dto.AttributeAddNewDTO;
import com.cooxiao.mall.pojo.product.dto.AttributeUpdateDTO;
import com.cooxiao.mall.pojo.product.model.Attribute;
import com.cooxiao.mall.pojo.product.vo.AttributeDetailsVO;
import com.cooxiao.mall.pojo.product.vo.AttributeStandardVO;
import com.cooxiao.mall.product.constant.DataCommonConst;
import com.cooxiao.mall.product.mapper.AttributeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>商品属性业务实现类</p>
 *
 * @author cooxiao.com QQ:25380243
 * @since 2021-11-30
 */
@Service
@Slf4j
public class AttributeServiceImpl implements IAttributeService {

    @Autowired
    private AttributeMapper attributeMapper;

    private AttributeStandardVO convertToVO(Attribute attribute) {
        if (attribute == null) {
            return null;
        }
        AttributeStandardVO vo = new AttributeStandardVO();
        BeanUtils.copyProperties(attribute, vo);
        return vo;
    }

    @Override
    public void addNew(AttributeAddNewDTO attributeAddnewDTO) {
        log.debug("增加商品属性：{}", attributeAddnewDTO);
        Attribute attribute = new Attribute();
        BeanUtils.copyProperties(attributeAddnewDTO, attribute);
        attribute.setSort(attributeAddnewDTO.getSort() == null ? DataCommonConst.SORT_DEFAULT : attributeAddnewDTO.getSort());
        int rows = attributeMapper.insert(attribute);
        if (rows != 1) {
            throw new CoolSharkServiceException(ResponseCode.INTERNAL_SERVER_ERROR, "新增商品属性失败，服务器忙，请稍后再次尝试！");
        }
    }

    @Override
    public void deleteById(Long id) {
        log.debug("删除商品属性，id={}", id);
        // 检查尝试删除的数据是否存在
        Object currentData = attributeMapper.getById(id);
        if (currentData == null) {
            throw new CoolSharkServiceException(ResponseCode.NOT_FOUND, "删除商品属性失败，尝试访问的数据不存在！");
        }

        // 执行删除
        log.debug("删除id为{}的商品属性数据", id);
        int rows = attributeMapper.deleteById(id);
        if (rows != 1) {
            throw new CoolSharkServiceException(ResponseCode.INTERNAL_SERVER_ERROR, "删除商品属性失败，服务器忙，请稍后再次尝试！");
        }
    }

    @Override
    public void updateById(Long id, AttributeUpdateDTO attributeUpdateDTO) {
        // 检查尝试更新的数据是否存在
        Object currentData = attributeMapper.getById(id);
        if (currentData == null) {
            throw new CoolSharkServiceException(ResponseCode.NOT_FOUND, "更新商品属性失败，尝试访问的数据不存在！");
        }

        // 执行更新
        Attribute attribute = new Attribute();
        log.debug("更新属性数据:" + attribute);
        BeanUtils.copyProperties(attributeUpdateDTO, attribute);
        attribute.setId(id);
        int rows = attributeMapper.updateById(attribute);
        if (rows != 1) {
            throw new CoolSharkServiceException(ResponseCode.INTERNAL_SERVER_ERROR, "更新商品属性失败，服务器忙，请稍后再次尝试！");
        }
    }

    @Override
    public AttributeDetailsVO getDetailsById(Long id) {
        AttributeDetailsVO attributeDetailsVO = attributeMapper.getDetailsById(id);
        if (attributeDetailsVO == null) {
            throw new CoolSharkServiceException(ResponseCode.NOT_FOUND, "获取商品属性详情（id=" + id + "）失败，尝试访问的数据不存在！");
        }
        return attributeDetailsVO;
    }

    @Override
    public JsonPage<AttributeStandardVO> listByTemplateId(Long templateId, Integer pageNum, Integer pageSize) {
        Page<Attribute> pageParam = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Attribute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attribute::getTemplateId, templateId)
               .orderByDesc(Attribute::getSort, Attribute::getGmtCreate);
        IPage<Attribute> result = attributeMapper.selectPage(pageParam, wrapper);
        List<AttributeStandardVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        IPage<AttributeStandardVO> pageVO = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageVO.setRecords(voList);
        return JsonPage.restPage(pageVO);
    }

    @Override
    public JsonPage<AttributeStandardVO> listSaleAttributesByTemplateId(Long templateId, Integer pageNum, Integer pageSize) {
        Page<Attribute> pageParam = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Attribute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attribute::getTemplateId, templateId)
               .eq(Attribute::getType, 1)
               .orderByDesc(Attribute::getSort, Attribute::getGmtCreate);
        IPage<Attribute> result = attributeMapper.selectPage(pageParam, wrapper);
        List<AttributeStandardVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        IPage<AttributeStandardVO> pageVO = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageVO.setRecords(voList);
        return JsonPage.restPage(pageVO);
    }

    @Override
    public JsonPage<AttributeStandardVO> listNonSaleAttributesByTemplateId(Long templateId, Integer pageNum, Integer pageSize) {
        Page<Attribute> pageParam = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Attribute> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Attribute::getTemplateId, templateId)
               .eq(Attribute::getType, 0)
               .orderByDesc(Attribute::getSort, Attribute::getGmtCreate);
        IPage<Attribute> result = attributeMapper.selectPage(pageParam, wrapper);
        List<AttributeStandardVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        IPage<AttributeStandardVO> pageVO = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageVO.setRecords(voList);
        return JsonPage.restPage(pageVO);
    }

}

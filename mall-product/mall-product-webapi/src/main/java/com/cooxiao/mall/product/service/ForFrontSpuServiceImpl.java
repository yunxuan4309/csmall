package com.cooxiao.mall.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.product.model.Spu;
import com.cooxiao.mall.pojo.product.query.SpuQuery;
import com.cooxiao.mall.pojo.product.vo.SpuDetailStandardVO;
import com.cooxiao.mall.pojo.product.vo.SpuListItemVO;
import com.cooxiao.mall.pojo.product.vo.SpuStandardVO;
import com.cooxiao.mall.product.service.front.IForFrontSpuService;
import com.cooxiao.mall.product.mapper.SpuDetailMapper;
import com.cooxiao.mall.product.mapper.SpuMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@DubboService
@Service
public class ForFrontSpuServiceImpl implements IForFrontSpuService {
    @Autowired
    private SpuMapper spuMapper;
    @Autowired
    private SpuDetailMapper spuDetailMapper;

    @Override
    public JsonPage<SpuListItemVO> listSpuByCategoryId(Long categoryId, Integer page, Integer pageSize) {
        Page<Spu> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Spu::getCategoryId, categoryId)
               .eq(Spu::getChecked, 1)
               .eq(Spu::getDeleted, 0)
               .orderByDesc(Spu::getGmtCreate);
        IPage<Spu> result = spuMapper.selectPage(pageParam, wrapper);
        // 转换为VO
        List<SpuListItemVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        IPage<SpuListItemVO> pageVO = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        pageVO.setRecords(voList);
        return JsonPage.restPage(pageVO);
    }

    /**
     * 和已有方法重复
     * @param id
     * @return
     */
    @Override
    public SpuStandardVO getSpuById(Long id) {
        SpuStandardVO spuStandardVO = spuMapper.getById(id);
        if (spuStandardVO == null) {
            throw new CoolSharkServiceException(ResponseCode.NOT_FOUND, "查询SPU详情失败，尝试访问的SPU数据不存在！");
        }
        return spuStandardVO;
    }

    @Override
    public SpuDetailStandardVO getSpuDetailById(Long spuId) {
        SpuDetailStandardVO spuDetailStandardVO = spuDetailMapper.getBySpuId(spuId);
        return spuDetailStandardVO;
    }

    @Override
    public JsonPage<Spu> getSpuByPage(Integer pageNum, Integer pageSize) {
        Page<Spu> pageParam = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Spu::getChecked, 1)
               .eq(Spu::getDeleted, 0)
               .orderByDesc(Spu::getGmtCreate);
        IPage<Spu> result = spuMapper.selectPage(pageParam, wrapper);
        return JsonPage.restPage(result);
    }

    private SpuListItemVO convertToVO(Spu spu) {
        if (spu == null) {
            return null;
        }
        SpuListItemVO vo = new SpuListItemVO();
        BeanUtils.copyProperties(spu, vo);
        return vo;
    }
}

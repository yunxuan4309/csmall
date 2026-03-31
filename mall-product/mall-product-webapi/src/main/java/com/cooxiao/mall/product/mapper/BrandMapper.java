package com.cooxiao.mall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cooxiao.mall.pojo.product.model.Brand;
import com.cooxiao.mall.pojo.product.vo.BrandStandardVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * <p>品牌Mapper接口</p>
 *
 * @author cooxiao.com QQ:25380243
 * @since 2021-11-30
 */
@Repository
public interface BrandMapper extends BaseMapper<Brand> {

    /**
     * 根据品牌id查询品牌详情
     *
     * @param id 品牌id
     * @return 匹配的品牌详情，如果没有匹配的数据，则返回null
     */
    BrandStandardVO getById(Long id);

    /**
     * 根据品牌名称查询品牌详情
     *
     * @param name 品牌名称
     * @return 匹配的品牌详情，如果没有匹配的数据，则返回null
     */
    BrandStandardVO getByName(String name);

    /**
     * 根据分类id查询品牌列表
     *
     * @param categoryId 分类id
     * @return 品牌列表，如果没有匹配的数据，将返回长度为0的列表
     */
    List<BrandStandardVO> listByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 更新品牌全部信息
     *
     * @param brand 品牌实体
     * @return 受影响的行数
     */
    int updateFullInfoById(Brand brand);

}

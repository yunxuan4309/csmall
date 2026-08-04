package com.cooxiao.mall.product.service;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.product.dto.SkuAddNewDTO;
import com.cooxiao.mall.pojo.product.dto.SkuGenerateDTO;
import com.cooxiao.mall.pojo.product.dto.SkuUpdateFullInfoDTO;
import com.cooxiao.mall.pojo.product.vo.SkuStandardVO;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>SKU（Stock Keeping Unit）业务接口</p>
 *
 * @author java.cooxiao.com
 * @since 2021-11-30
 */
public interface ISkuService {

    /**
     * 增加SKU信息
     *
     * @param skuAddNewDTO 新增的SKU对象
     */
    @Transactional
    void addNew(SkuAddNewDTO skuAddNewDTO);

    /**
     * 更新SKU的全部信息
     *
     * @param id                   被修改的SKU信息的id
     * @param skuUpdateFullInfoDTO 新的相关值的对象
     */
    @Transactional
    void updateFullInfoById(Long id, SkuUpdateFullInfoDTO skuUpdateFullInfoDTO);

    /**
     * 根据SKU id查询SKU详情
     *
     * @param id SKU id
     * @return 匹配的SKU详情，如果没有匹配的数据，则返回null
     */
    SkuStandardVO getById(Long id);

    /**
     * 查询某SPU下的SKU列表
     *
     * @param spuId    SPU id
     * @param pageNum  页码
     * @param pageSize 每页记录数
     * @return SPU下的SKU的列表，如果无记录，则返回长度为0的列表
     */
    JsonPage<SkuStandardVO> list(Long spuId, Integer pageNum, Integer pageSize);

    /**
     * 根据属性模板选择的销售属性值，笛卡尔积生成 SKU 组合并批量入库
     *
     * @param skuGenerateDTO 生成请求（含 SPU id 和销售属性选择）
     * @return 生成的 SKU 数量
     */
    @Transactional
    int generateSkus(SkuGenerateDTO skuGenerateDTO);

    /**
     * 删除SKU（连同其规格明细）
     *
     * @param id SKU id
     */
    @Transactional
    void deleteById(Long id);

}

package com.cooxiao.mall.product.service;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.product.dto.PictureAddNewBatchDTO;
import com.cooxiao.mall.pojo.product.dto.PictureAddNewDTO;
import com.cooxiao.mall.pojo.product.dto.PictureUploadBatchDTO;
import com.cooxiao.mall.pojo.product.dto.PictureUploadSingleDTO;
import com.cooxiao.mall.pojo.product.vo.PictureSimpleVO;
import com.cooxiao.mall.pojo.product.vo.PictureStandardVO;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>图片业务接口</p>
 *
 * @author java.cooxiao.com
 * @since 2021-11-30
 */
public interface IPictureService {

    /**
     * 增加图片记录
     *
     * @param pictureAddNewDTO 图片数据
     */
    void addNew(PictureAddNewDTO pictureAddNewDTO);

    /**
     * 绑定多张图片到相册
     *
     * @param pictureDTO 上传的图片数据的数组
     */
    @Deprecated
    void bindBatchToAlbum(PictureAddNewBatchDTO[] pictureDTO);

    /**
     * 上传图片（单张图片）
     *
     * @param pictureUploadSingleDTO 封装了图片数据的对象
     * @return 图片基本信息
     */
    PictureSimpleVO uploadSingle(PictureUploadSingleDTO pictureUploadSingleDTO);

    /**
     * 上传图片到相册
     *
     * @param albumId                相册id
     * @param pictureUploadSingleDTO 封装了图片数据的对象
     * @return 图片基本信息
     */
    PictureSimpleVO uploadSingleToAlbum(Long albumId, PictureUploadSingleDTO pictureUploadSingleDTO);

    /**
     * 上传图片（批量上传）
     *
     * @param pictureUploadBatchDTO 封装了图片数据的对象的集合
     * @return 图片基本信息的集合
     */
    List<PictureSimpleVO> uploadBatch(PictureUploadBatchDTO pictureUploadBatchDTO);

    /**
     * 将指定的图片设置为"封面"
     *
     * @param id 图片id
     */
    @Transactional
    void setCover(Long id);

    /**
     * 查询相册中的图片列表
     *
     * @param albumId  相册id
     * @param pageNum  页码
     * @param pageSize 每页记录数
     * @return 图片的列表，如果无记录，则返回长度为0的列表
     */
    JsonPage<PictureStandardVO> listByAlbumId(Long albumId, Integer pageNum, Integer pageSize);

}

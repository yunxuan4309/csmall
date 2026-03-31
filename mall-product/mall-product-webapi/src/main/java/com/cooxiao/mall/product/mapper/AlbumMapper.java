package com.cooxiao.mall.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cooxiao.mall.pojo.product.model.Album;
import com.cooxiao.mall.pojo.product.vo.AlbumStandardVO;
import org.springframework.stereotype.Repository;

/**
 * <p>相册Mapper接口</p>
 *
 * @author cooxiao.com QQ:25380243
 * @since 2021-11-30
 */
@Repository
public interface AlbumMapper extends BaseMapper<Album> {

    /**
     * 根据相册id查询相册详情
     *
     * @param id 相册id
     * @return 匹配的相册详情，如果没有匹配的数据，则返回null
     */
    AlbumStandardVO getById(Long id);

    /**
     * 根据相册名称查询相册详情
     *
     * @param name 相册名称
     * @return 匹配的相册详情，如果没有匹配的数据，则返回null
     */
    AlbumStandardVO getByName(String name);

}

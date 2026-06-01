package com.cooxiao.mall.resource.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cooxiao.mall.resource.pojo.entity.UploadRecord;
import com.cooxiao.mall.resource.vo.UploadRecordVO;

/**
 * 用户上传记录服务接口
 */
public interface IUploadRecordService {

    /**
     * 保存上传记录
     */
    void saveRecord(UploadRecord record);

    /**
     * 分页查询用户的图片上传记录（按时间倒序）
     */
    IPage<UploadRecordVO> listUserRecords(Long userId, Integer page, Integer pageSize);
}

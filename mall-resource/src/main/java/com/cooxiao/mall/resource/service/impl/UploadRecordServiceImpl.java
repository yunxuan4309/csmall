package com.cooxiao.mall.resource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cooxiao.mall.resource.mapper.UploadRecordMapper;
import com.cooxiao.mall.resource.pojo.entity.UploadRecord;
import com.cooxiao.mall.resource.service.IUploadRecordService;
import com.cooxiao.mall.resource.vo.UploadRecordVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户上传记录服务实现
 */
@Service
public class UploadRecordServiceImpl implements IUploadRecordService {

    @Autowired
    private UploadRecordMapper uploadRecordMapper;

    @Override
    public void saveRecord(UploadRecord record) {
        record.setId(IdWorker.getId());
        LocalDateTime now = LocalDateTime.now();
        record.setGmtCreate(now);
        record.setGmtModified(now);
        uploadRecordMapper.insert(record);
    }

    @Override
    public IPage<UploadRecordVO> listUserRecords(Long userId, Integer page, Integer pageSize) {
        // 构建分页条件
        Page<UploadRecord> pageParam = new Page<>(page, pageSize);

        // 查询条件：按用户 ID 过滤，按创建时间倒序
        LambdaQueryWrapper<UploadRecord> wrapper = new LambdaQueryWrapper<UploadRecord>()
                .eq(UploadRecord::getUserId, userId)
                .orderByDesc(UploadRecord::getGmtCreate);

        // 执行分页查询
        Page<UploadRecord> recordPage = uploadRecordMapper.selectPage(pageParam, wrapper);

        // 转换为 VO
        Page<UploadRecordVO> voPage = new Page<>(recordPage.getCurrent(), recordPage.getSize(), recordPage.getTotal());
        List<UploadRecordVO> voList = new ArrayList<>();
        for (UploadRecord record : recordPage.getRecords()) {
            UploadRecordVO vo = new UploadRecordVO();
            BeanUtils.copyProperties(record, vo);
            if (record.getGmtCreate() != null) {
                vo.setGmtCreate(record.getGmtCreate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            voList.add(vo);
        }
        voPage.setRecords(voList);
        voPage.setTotal(recordPage.getTotal());
        voPage.setPages(recordPage.getPages());

        return voPage;
    }
}

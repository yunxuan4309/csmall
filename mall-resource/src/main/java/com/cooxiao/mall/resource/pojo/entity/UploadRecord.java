package com.cooxiao.mall.resource.pojo.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户上传文件记录表实体类
 */
@Data
@TableName("res_upload_record")
public class UploadRecord {

    /**
     * 记录 id
     */
    private Long id;

    /**
     * 上传用户 id
     */
    private Long userId;

    /**
     * 上传用户名（冗余）
     */
    private String username;

    /**
     * 文件访问 URL
     */
    private String url;

    /**
     * 文件类型（如 image/jpeg）
     */
    private String contentType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 图片宽度（px）
     */
    private Integer width;

    /**
     * 图片高度（px）
     */
    private Integer height;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 数据创建时间
     */
    private LocalDateTime gmtCreate;

    /**
     * 数据最后修改时间
     */
    private LocalDateTime gmtModified;
}

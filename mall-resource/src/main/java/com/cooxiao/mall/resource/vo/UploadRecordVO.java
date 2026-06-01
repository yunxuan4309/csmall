package com.cooxiao.mall.resource.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 用户上传记录 VO
 */
@Data
public class UploadRecordVO {

    @ApiModelProperty("记录 id")
    private Long id;

    @ApiModelProperty("文件访问 URL")
    private String url;

    @ApiModelProperty("文件类型")
    private String contentType;

    @ApiModelProperty("文件大小（字节）")
    private Long fileSize;

    @ApiModelProperty("图片宽度（px）")
    private Integer width;

    @ApiModelProperty("图片高度（px）")
    private Integer height;

    @ApiModelProperty("原始文件名")
    private String originalFilename;

    @ApiModelProperty("上传时间")
    private String gmtCreate;
}

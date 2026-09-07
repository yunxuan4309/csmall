package com.cooxiao.mall.pojo.ums.dto;

import com.cooxiao.mall.pojo.valid.ums.DeliveryAddressRegExpression;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * <p>
 * 用户收货地址表（编辑）
 * </p>
 * <p>⚠️ 仅强制 id 必填；其余字段"有则验格式"（不强制非空）——编辑走动态更新，
 * 允许只改部分字段。与新增 DTO 的必填策略不同是有意为之。</p>
 *
 * @author cooxiao.com
 * @since 2022-02-22
 */
@ApiModel(value = "编辑收货地址DTO")
@Data
public class DeliveryAddressEditDTO implements DeliveryAddressRegExpression, Serializable {

    private static final long serialVersionUID = 1L;

    private static final String MESSAGE_PREFIX = "编辑地址失败，";

    /**
     * 地址ID（必填——由前端传递，updateById 的更新目标）
     */
    @ApiModelProperty(value = "地址ID", name = "id", required = true, example = "1")
    @NotNull(message = MESSAGE_PREFIX + "请提供要编辑的地址ID！")
    private Long id;

    /**
     * 联系人姓名（有则验格式，不强制非空——部分更新）
     */
    @ApiModelProperty(value = "联系人姓名", name = "contactName", example = "王先生")
    @Pattern(regexp = REGEXP_CONTACT_NAME, message = MESSAGE_PREFIX + MESSAGE_CONTACT_NAME)
    private String contactName;

    /**
     * 联系电话（有则验格式）
     */
    @ApiModelProperty(value = "联系电话", name = "mobilePhone", example = "18899997788")
    @Pattern(regexp = REGEXP_MOBILE_PHONE, message = MESSAGE_PREFIX + MESSAGE_MOBILE_PHONE)
    private String mobilePhone;

    /**
     * 固定电话（有则验格式）
     */
    @ApiModelProperty(value = "固定电话", name = "telephone", example = "010-66775566")
    @Pattern(regexp = REGEXP_TELEPHONE, message = MESSAGE_PREFIX + MESSAGE_TELEPHONE)
    private String telephone;

    /**
     * 省-代号（有则验格式）
     */
    @ApiModelProperty(value = "省-代号", name = "provinceCode", example = "110000")
    @Pattern(regexp = REGEXP_REGION_CODE, message = MESSAGE_PREFIX + MESSAGE_REGION_CODE)
    private String provinceCode;

    /**
     * 省-名称（有则验格式，不强制非空）
     */
    @ApiModelProperty(value = "省-名称", name = "provinceName", example = "北京")
    private String provinceName;

    /**
     * 市-代号（有则验格式）
     */
    @ApiModelProperty(value = "市-代号", name = "cityCode", example = "110000")
    @Pattern(regexp = REGEXP_REGION_CODE, message = MESSAGE_PREFIX + MESSAGE_REGION_CODE)
    private String cityCode;

    /**
     * 市-名称（有则验格式，不强制非空）
     */
    @ApiModelProperty(value = "市-名称", name = "cityName", example = "北京")
    private String cityName;

    /**
     * 区-代号（有则验格式）
     */
    @ApiModelProperty(value = "区-代号", name = "districtCode", example = "110103")
    @Pattern(regexp = REGEXP_REGION_CODE, message = MESSAGE_PREFIX + MESSAGE_REGION_CODE)
    private String districtCode;

    /**
     * 区-名称（有则验格式，不强制非空）
     */
    @ApiModelProperty(value = "区-名称", name = "districtName", example = "海淀")
    private String districtName;

    /**
     * 街道-代号（选填）
     */
    @ApiModelProperty(value = "街道-代号", name = "streetCode", example = "00005")
    private String streetCode;

    /**
     * 街道-名称（选填）
     */
    @ApiModelProperty(value = "街道-名称", name = "streetName", example = "中关村街道")
    private String streetName;

    /**
     * 详细地址（有则验格式）
     */
    @ApiModelProperty(value = "详细地址", name = "detailedAddress", example = "中关村软件园28-3-405")
    @Size(max = 100, message = MESSAGE_PREFIX + MESSAGE_DETAILED_ADDRESS)
    private String detailedAddress;

    /**
     * 标签（选填）
     */
    @ApiModelProperty(value = "标签，例如：家、公司、学校", name = "tag", example = "公司")
    private String tag;

    /**
     * 是否为默认地址（有则验取值）
     */
    @ApiModelProperty(value = "是否为默认地址，1=默认，0=非默认", name = "defaultAddress", example = "0")
    private Integer defaultAddress;
}

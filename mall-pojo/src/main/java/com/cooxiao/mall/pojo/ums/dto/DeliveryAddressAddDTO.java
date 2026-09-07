package com.cooxiao.mall.pojo.ums.dto;

import com.cooxiao.mall.pojo.valid.ums.DeliveryAddressRegExpression;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * <p>
 * 用户收货地址表（新增）
 * </p>
 *
 * @author cooxiao.com
 * @since 2022-02-22
 */
@ApiModel(value = "新增收货地址DTO")
@Data
public class DeliveryAddressAddDTO implements DeliveryAddressRegExpression, Serializable {

    private static final long serialVersionUID = 1L;

    private static final String MESSAGE_PREFIX = "新增地址失败，";

    /**
     * 联系人姓名（必填）
     */
    @ApiModelProperty(value = "联系人姓名", name = "contactName", required = true, example = "王先生")
    @NotBlank(message = MESSAGE_PREFIX + "请填写联系人姓名！")
    @Pattern(regexp = REGEXP_CONTACT_NAME, message = MESSAGE_PREFIX + MESSAGE_CONTACT_NAME)
    private String contactName;

    /**
     * 联系电话（与 fixedPhone 至少填一个，二选一校验在 Service 层）
     */
    @ApiModelProperty(value = "联系电话", name = "mobilePhone", example = "18899997788")
    @Pattern(regexp = REGEXP_MOBILE_PHONE, message = MESSAGE_PREFIX + MESSAGE_MOBILE_PHONE)
    private String mobilePhone;

    /**
     * 固定电话（与 mobilePhone 至少填一个，二选一校验在 Service 层）
     */
    @ApiModelProperty(value = "固定电话", name = "telephone", example = "010-66775566")
    @Pattern(regexp = REGEXP_TELEPHONE, message = MESSAGE_PREFIX + MESSAGE_TELEPHONE)
    private String telephone;

    /**
     * 省-代号（有则验格式，与省名称同传）
     */
    @ApiModelProperty(value = "省-代号", name = "provinceCode", example = "110000")
    @Pattern(regexp = REGEXP_REGION_CODE, message = MESSAGE_PREFIX + MESSAGE_REGION_CODE)
    private String provinceCode;

    /**
     * 省-名称（必填）
     */
    @ApiModelProperty(value = "省-名称", name = "provinceName", required = true, example = "北京")
    @NotBlank(message = MESSAGE_PREFIX + "请填写省份！")
    private String provinceName;

    /**
     * 市-代号（有则验格式）
     */
    @ApiModelProperty(value = "市-代号", name = "cityCode", example = "110000")
    @Pattern(regexp = REGEXP_REGION_CODE, message = MESSAGE_PREFIX + MESSAGE_REGION_CODE)
    private String cityCode;

    /**
     * 市-名称（必填）
     */
    @ApiModelProperty(value = "市-名称", name = "cityName", required = true, example = "北京")
    @NotBlank(message = MESSAGE_PREFIX + "请填写城市！")
    private String cityName;

    /**
     * 区-代号（有则验格式）
     */
    @ApiModelProperty(value = "区-代号", name = "districtCode", example = "110103")
    @Pattern(regexp = REGEXP_REGION_CODE, message = MESSAGE_PREFIX + MESSAGE_REGION_CODE)
    private String districtCode;

    /**
     * 区-名称（必填）
     */
    @ApiModelProperty(value = "区-名称", name = "districtName", required = true, example = "海淀")
    @NotBlank(message = MESSAGE_PREFIX + "请填写区县！")
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
     * 详细地址（必填，≤100 字）
     */
    @ApiModelProperty(value = "详细地址", name = "detailedAddress", required = true, example = "中关村软件园28-3-405")
    @NotBlank(message = MESSAGE_PREFIX + "请填写详细地址！")
    @Size(max = 100, message = MESSAGE_PREFIX + MESSAGE_DETAILED_ADDRESS)
    private String detailedAddress;

    /**
     * 标签（选填），例如：家、公司、学校
     */
    @ApiModelProperty(value = "标签，例如：家、公司、学校", name = "tag", example = "公司")
    private String tag;

    /**
     * 是否为默认地址（选填），1=默认，0=非默认（Service 首个地址自动设默认）
     */
    @ApiModelProperty(value = "是否为默认地址，1=默认，0=非默认", name = "defaultAddress", example = "0")
    private Integer defaultAddress;
}

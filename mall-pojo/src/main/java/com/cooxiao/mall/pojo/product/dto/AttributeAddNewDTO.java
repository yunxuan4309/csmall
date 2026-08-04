package com.cooxiao.mall.pojo.product.dto;

import com.cooxiao.mall.pojo.valid.product.AttributeRegExpression;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;

@Data
public class AttributeAddNewDTO implements AttributeRegExpression, Serializable {

    /**
     * 验证请求参数失败的描述文本前缀
     */
    private static final String VALIDATE_MESSAGE_PREFIX = "新增属性失败，";

    /**
     * 所属属性模板id
     */
    @ApiModelProperty(value = "所属属性模板id", required = true)
    @NotNull(message = VALIDATE_MESSAGE_PREFIX + "请选择所属属性模板！")
    @Min(value = 1, message = VALIDATE_MESSAGE_PREFIX + "选择的所属属性模板的数据格式错误！")
    private Long templateId;

    /**
     * 属性名称
     */
    @ApiModelProperty(value = "属性名称", required = true)
    @NotNull(message = VALIDATE_MESSAGE_PREFIX + "请填写名称！")
    @Pattern(regexp = REGEXP_NAME, message = VALIDATE_MESSAGE_PREFIX + MESSAGE_NAME)
    private String name;

    /**
     * 简介（某些属性名称可能相同，通过简介补充描述）
     */
    @ApiModelProperty(value = "简介（某些属性名称可能相同，通过简介补充描述）", required = true)
    @NotNull(message = VALIDATE_MESSAGE_PREFIX + "请填写简介！")
    @Pattern(regexp = REGEXP_DESCRIPTION, message = VALIDATE_MESSAGE_PREFIX + MESSAGE_DESCRIPTION)
    private String description;

    /**
     * 属性类型（决定此属性是否参与生成 SKU 组合）
     * 1 = 销售属性：决定 SKU 组合维度，如"存储容量×颜色"生成多个 SKU
     * 0 = 参数属性：仅用于展示，不参与 SKU 生成，如"屏幕尺寸""电池容量"
     * 例：手机模板 → 存储容量(1),颜色(1),运行内存(1) → 笛卡尔积生成 SKU
     */
    @ApiModelProperty(value = "属性类型，1=销售属性，0=非销售属性", required = true)
    @NotNull(message = VALIDATE_MESSAGE_PREFIX + "请选择类型！")
    @Range(max = 1, message = VALIDATE_MESSAGE_PREFIX + "选择的属性类型的数据格式错误！")
    private Integer type;

    /**
     * 属性值输入类型（决定前端表单如何渲染选择器）
     * 0 = 手动录入：自由文本输入
     * 1 = 单选：仅能选一个值
     * 2 = 多选：可同时选多个值
     * 3 = 单选下拉：下拉列表单选
     * 4 = 多选下拉：下拉列表多选
     * 例：存储容量(1=单选:128/256/512 选一个)，颜色(1=单选:黑/白选一个)
     */
    @ApiModelProperty(value = "属性值输入类型，0=手动录入，1=单选，2=多选， 3=单选（下拉列表），4=多选（下拉列表）", required = true)
    @NotNull(message = VALIDATE_MESSAGE_PREFIX + "请选择属性值输入类型！")
    @Range(max = 4, message = VALIDATE_MESSAGE_PREFIX + "选择的属性值输入类型的数据格式错误！")
    private Integer inputType;

    /**
     * 备选值列表（JSON 数组格式，如 ["128GB","256GB","512GB"]）
     * 手动录入(inputType=0)时可不填，其余类型需填
     */
    @ApiModelProperty(value = "备选值列表")
    @Pattern(regexp = REGEXP_VALUE_LIST, message = VALIDATE_MESSAGE_PREFIX + MESSAGE_VALUE_LIST)
    private String valueList;

    /**
     * 计量单位
     */
    @ApiModelProperty(value = "计量单位")
    @Pattern(regexp = REGEXP_UNIT, message = VALIDATE_MESSAGE_PREFIX + MESSAGE_UNIT)
    private String unit;

    /**
     * 自定义排序序号
     */
    @ApiModelProperty(value = "自定义排序序号")
    @Range(max = 99, message = VALIDATE_MESSAGE_PREFIX + MESSAGE_SORT)
    private Integer sort;

    /**
     * 是否允许用户在预设值之外自定义输入
     * 1 = 允许自定义（如 T 恤定制尺寸，顾客可以自己写"XXXL"）
     * 0 = 禁止自定义（如手机颜色，顾客必须从预设色里选）
     * 一般设 0 即可
     */
    @ApiModelProperty(value = "是否允许自定义，1=允许，0=禁止")
    @Range(max = 1, message = VALIDATE_MESSAGE_PREFIX + "选择的是否允许自定义的数据格式错误！")
    private Integer allowCustomize;

}

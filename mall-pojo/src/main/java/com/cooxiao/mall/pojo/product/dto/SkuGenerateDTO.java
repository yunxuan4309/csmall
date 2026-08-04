package com.cooxiao.mall.pojo.product.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 根据属性模板批量生成 SKU 的请求体
 */
@Data
public class SkuGenerateDTO implements Serializable {

    /**
     * SPU id
     */
    @NotNull(message = "生成SKU失败，SPU id不能为空！")
    @ApiModelProperty(value = "SPU id", required = true)
    private Long spuId;

    /**
     * 选择的销售属性值列表（每个属性选一个或多个值，做笛卡尔积）
     */
    @NotEmpty(message = "生成SKU失败，请选择至少一个属性！")
    @Valid
    @ApiModelProperty(value = "销售属性选择列表")
    private List<SelectedAttribute> attributes;

    /**
     * 单个销售属性的值选择
     */
    @Data
    public static class SelectedAttribute implements Serializable {

        @NotNull(message = "生成SKU失败，属性id不能为空！")
        @ApiModelProperty(value = "属性id", required = true)
        private Long attributeId;

        @NotNull(message = "生成SKU失败，属性名不能为空！")
        @ApiModelProperty(value = "属性名", required = true)
        private String attributeName;

        @NotEmpty(message = "生成SKU失败，属性值不能为空！")
        @ApiModelProperty(value = "选中的属性值列表，如 [\"128GB\",\"256GB\"]", required = true)
        private List<String> values;
    }
}

package com.cooxiao.mall.pojo.ai.dto;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ProductCompareDTO implements Serializable {

    @ApiModelProperty(value = "要对比的SPU ID列表（2~4个）", required = true, example = "[1, 2, 3]")
    @NotEmpty(message = "请至少选择2个商品进行对比")
    @Size(min = 2, max = 4, message = "每次对比2~4个商品")
    private List<Long> spuIds;

    @ApiModelProperty(value = "对比维度，不传则使用默认维度", example = "[\"规格参数\", \"价格\", \"核心卖点\", \"适用场景\", \"优缺点\"]")
    private List<String> dimensions;
}

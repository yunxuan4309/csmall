package com.cooxiao.mall.common.restful;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * <p>数据分页类</p>
 *
 * @param <T> 列表数据
 */
@Data
public class JsonPage<T> implements Serializable {

    @ApiModelProperty(value = "当前页码", name = "page")
    private Integer page;
    @ApiModelProperty(value = "分页条数", name = "pageSize")
    private Integer pageSize;
    @ApiModelProperty(value = "总页数", name = "totalPage")
    private Integer totalPage;
    @ApiModelProperty(value = "总记录数", name = "total")
    private Long total;
    @ApiModelProperty(value = "分页数据", name = "list")
    private List<T> list;

    /**
     * 将MyBatis-Plus分页后的IPage转为分页信息
     */
    public static <T> JsonPage<T> restPage(IPage<T> page) {
        JsonPage<T> result = new JsonPage<T>();
        result.setTotalPage((int) page.getPages());
        result.setPage((int) page.getCurrent());
        result.setPageSize((int) page.getSize());
        result.setTotal(page.getTotal());
        result.setList(page.getRecords());
        return result;
    }
}

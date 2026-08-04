package com.cooxiao.mall.product.controller;

import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.product.dto.PictureAddNewBatchDTO;
import com.cooxiao.mall.pojo.product.dto.PictureAddNewDTO;
import com.cooxiao.mall.pojo.product.dto.PictureUploadBatchDTO;
import com.cooxiao.mall.pojo.product.dto.PictureUploadSingleDTO;
import com.cooxiao.mall.pojo.product.vo.PictureSimpleVO;
import com.cooxiao.mall.pojo.product.vo.PictureStandardVO;
import com.cooxiao.mall.product.constant.WebConst;
import com.cooxiao.mall.product.service.IPictureService;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * <p>图片控制器</p>
 *
 * @author cooxiao.com
 * @since 2021-11-30
 */
@Api(tags = "5. 图片管理模块")
@RestController
@RequestMapping("/pms/pictures")
public class PictureController {

    @Autowired
    private IPictureService pictureService;

    /**
     * 新增图片记录
     */
    @ApiOperationSupport(order = 10)
    @ApiOperation(value = "新增图片记录", notes = "需要商品后台【写】权限：/pms/product/update")
    @PreAuthorize("hasAuthority('/pms/product/update')")
    @PostMapping("/addnew")
    public JsonResult<Void> addNew(@Valid @RequestBody PictureAddNewDTO pictureAddNewDTO) {
        pictureService.addNew(pictureAddNewDTO);
        return JsonResult.ok();
    }

    /**
     * 将图片设置为封面
     */
    @ApiOperationSupport(order = 30)
    @ApiOperation(value = "将图片设置为封面", notes = "需要商品后台【写】权限：/pms/product/update")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "图片id", paramType = "path", required = true, dataType = "long")
    })
    @PreAuthorize("hasAuthority('/pms/product/update')")
    @PostMapping("/{id:[0-9]+}/set-cover")
    public JsonResult<Void> setCover(@PathVariable Long id) {
        pictureService.setCover(id);
        return JsonResult.ok();
    }

    /**
     * 根据相册查询图片列表
     */
    @ApiOperationSupport(order = 40)
    @ApiOperation(value = "根据相册查询图片列表", notes = "需要商品后台【读】权限：/pms/product/read")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "albumId", value = "相册id", required = true, dataType = "long"),
            @ApiImplicitParam(name = "page", value = "页码", dataType = "int"),
            @ApiImplicitParam(name = "pageSize", value = "每页记录数", dataType = "int")
    })
    @PreAuthorize("hasAuthority('/pms/product/read')")
    @GetMapping("")
    public JsonResult<JsonPage<PictureStandardVO>> listPictures(
            @RequestParam Long albumId,
            @RequestParam(required = false, defaultValue = WebConst.DEFAULT_PAGE) Integer page,
            @RequestParam(required = false, defaultValue = WebConst.DEFAULT_PAGE_SIZE) Integer pageSize) {
        JsonPage<PictureStandardVO> pictures = pictureService.listByAlbumId(albumId, page, pageSize);
        return JsonResult.ok(pictures);
    }

}

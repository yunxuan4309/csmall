package com.cooxiao.mall.resource.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.common.utils.JwtTokenUtils;
import com.cooxiao.mall.resource.service.IUploadRecordService;
import com.cooxiao.mall.resource.vo.UploadRecordVO;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户上传记录查询控制器
 */
@Api(tags = "2. 用户上传记录")
@RequestMapping("/upload/user")
@RestController
@Slf4j
public class UserUploadController {

    @Autowired
    private JwtTokenUtils jwtTokenUtils;
    @Autowired
    private IUploadRecordService uploadRecordService;

    @Value("${jwt.tokenHead}")
    private String jwtTokenHead;

    @ApiOperationSupport(order = 20)
    @ApiOperation("查询当前登录用户的图片上传记录（分页）")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "page", value = "页码", defaultValue = "1"),
            @ApiImplicitParam(name = "pageSize", value = "每页条数", defaultValue = "10")
    })
    @GetMapping("/list")
    public JsonResult<JsonPage<UploadRecordVO>> listUserRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest request) {

        // 从请求中提取当前用户
        CsmallAuthenticationInfo userInfo = extractUserFromRequest(request);
        if (userInfo == null) {
            throw new CoolSharkServiceException(ResponseCode.UNAUTHORIZED, "请先登录");
        }

        // 分页查询
        IPage<UploadRecordVO> recordPage = uploadRecordService.listUserRecords(userInfo.getId(), page, pageSize);

        // 转换为 JsonPage 并返回
        return JsonResult.ok(JsonPage.restPage(recordPage));
    }

    /**
     * 从请求中提取当前登录用户的信息
     */
    private CsmallAuthenticationInfo extractUserFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(jwtTokenHead)) {
            String authToken = authHeader.substring(jwtTokenHead.length()).trim();
            return jwtTokenUtils.getUserInfo(authToken);
        }
        return null;
    }
}

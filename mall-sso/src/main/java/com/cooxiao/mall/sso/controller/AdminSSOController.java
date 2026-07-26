package com.cooxiao.mall.sso.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.cooxiao.mall.sso.pojo.dto.AdminLoginDTO;
import com.cooxiao.mall.sso.security.service.admin.IAdminSSOService;
import com.cooxiao.mall.common.restful.JsonResult;

import com.cooxiao.mall.sso.pojo.vo.TokenVO;
import com.cooxiao.mall.sso.utils.LoginUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * <p>管理员单点登录控制器</p>
 */
@RestController
@RequestMapping("/admin/sso")
@Api(tags = "后台管理用户认证")
@Slf4j
public class AdminSSOController {

    @Autowired
    private IAdminSSOService adminSSOService;
    @Value("${jwt.tokenHead}")
    private String jwtTokenHead;

    /**
     * <p>登录login</p>
     * <p>主要就是验证是否登录账号密码正确</p>
     */
    @ApiOperation(value = "后台单点登录认证登录")
    @PostMapping("/login")
    @SentinelResource(value = "adminLogin", blockHandler = "loginBlock")
    public JsonResult<TokenVO> doLogin(@Valid @RequestBody AdminLoginDTO adminLoginDTO, HttpServletRequest request){
        //先补充数据
        String remoteAddr = LoginUtils.getIpAddress(request);//如果是localhost访问会记录ipv6格式的本机地址,正常
        log.info("远程ip地址:{}",remoteAddr);
        log.info("接收到的登录DTO: {}", adminLoginDTO);
        String userAgent=request.getHeader("User-Agent");
        log.info("远程客户端:{}",userAgent);
        adminLoginDTO.setIp(remoteAddr);
        adminLoginDTO.setUserAgent(userAgent);
        String token = adminSSOService.doLogin(adminLoginDTO);
        TokenVO tokenVO = new TokenVO();
        tokenVO.setTokenHeader(jwtTokenHead);
        tokenVO.setTokenValue(token);
        return JsonResult.ok(tokenVO);
    }

    /**
     * <p>登出logout</p>
     * <p>没有任何实际业务逻辑</p>
     */
    @ApiOperation(value = "单点登录认证登出")
    @PostMapping("/logout")
    public JsonResult doLogout(@RequestHeader(name = "Authorization") String token){
        adminSSOService.doLogout(token);
        return JsonResult.ok();
    }

    /** Sentinel 限流兜底：登录过于频繁 */
    public JsonResult<TokenVO> loginBlock(AdminLoginDTO dto, HttpServletRequest request, BlockException e) {
        log.warn("管理员登录被 Sentinel 限流，IP: {}", LoginUtils.getIpAddress(request));
        JsonResult<TokenVO> result = new JsonResult<>();
        result.setState(429);
        result.setMessage("登录过于频繁，请 1 分钟后再试");
        return result;
    }

}

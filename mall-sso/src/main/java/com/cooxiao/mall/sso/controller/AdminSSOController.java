package com.cooxiao.mall.sso.controller;

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
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.BufferedReader;
import java.io.IOException;

/**
 * <p>管理员单点登录控制器</p>
 */
@RestController
@RequestMapping("/admin/sso")
@Api(tags = "后台管理用户认证")
@Slf4j
public class AdminSSOController {

    /**
     * 调试端点：直接读取原始请求体
     */
    @PostMapping("/debug")
    @ApiOperation(value = "调试端点")
    public JsonResult<String> debug(HttpServletRequest request) {
        String contentType = request.getContentType();
        log.info("Content-Type: {}", contentType);
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        } catch (IOException e) {
            log.error("读取请求体失败", e);
        }
        log.info("原始请求体: {}", body);
        return JsonResult.ok(body.toString());
    }

    /**
     * 生成 BCrypt 密码哈希（临时调试用）
     */
    @GetMapping("/hash")
    @ApiOperation(value = "生成密码哈希")
    public JsonResult<String> generateHash(@RequestParam String password) {
        String hash = passwordEncoder.encode(password);
        return JsonResult.ok("密码: " + password + " => 哈希: " + hash);
    }
    /**
     * 测试代码
     * @param authentication
     * @return
     */
    @Deprecated
    @GetMapping("/home")
    @ApiOperation(value="测试用户数据认证对象生成问题")
    private JsonResult home(Authentication authentication){
        return JsonResult.ok(authentication);
    }
    @Autowired
    private IAdminSSOService adminSSOService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Value("${jwt.tokenHead}")
    private String jwtTokenHead;

    /**
     * <p>登录login</p>
     * <p>主要就是验证是否登录账号密码正确</p>
     */
    @ApiOperation(value = "后台单点登录认证登录")
    @PostMapping("/login")
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

}

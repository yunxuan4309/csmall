package com.cooxiao.mall.ums.controller;


import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.common.utils.JwtTokenUtils;
import com.cooxiao.mall.pojo.ums.dto.ChangePasswordDTO;
import com.cooxiao.mall.pojo.ums.dto.UserRegistryDTO;
import com.cooxiao.mall.pojo.ums.model.User;
import com.cooxiao.mall.ums.service.IUserService;
import com.cooxiao.mall.ums.utils.LoginUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.Data;

/**
 * <p>
 * 用户基本（常用）信息表 前端控制器
 * </p>
 *
 * @author cooxiao.com
 * @since 2022-02-22
 */
@RestController
@RequestMapping("/ums/user")
@Api(tags = "用户信息功能")
public class UserController {
    @Autowired
    private IUserService userService;
    @Autowired
    private JwtTokenUtils jwtTokenUtils;
    /**
     * 注册用户
     * @param userRegistyDTO
     * @return
     */
    @ApiOperation(value="注册用户")
    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    public JsonResult doRegister(@RequestBody UserRegistryDTO userRegistyDTO){
        User user = userService.doRegister(userRegistyDTO);
        return JsonResult.ok(new RegisterUserVO(user.getId(), user.getUsername(), user.getNickname()));
    }

    @ApiOperation(value="校验手机,邮箱,用户名是否重复")
    @PostMapping("/checkValue")
    @PreAuthorize("permitAll()")
    @ApiImplicitParams({
            @ApiImplicitParam(value="校验值",name="value",type = "string"),
            @ApiImplicitParam(value="校验值类型,phone,username,email",name="type",type = "string")
    })
    public JsonResult checkValue(String value,String type){
        userService.checkValue(value,type);
        return JsonResult.ok();
    }
    @Value("${jwt.tokenHead}")
    private String tokenHead;//Bearer
    /**
     * 修改登录密码
     */
    @ApiOperation(value="修改登录密码")
    @PostMapping("/renew/password")
    @PreAuthorize("hasRole('user')")
    public JsonResult renewPassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO, HttpServletRequest request,@RequestHeader("Authorization") String authToken){
        String ip = LoginUtils.getIpAddress(request);
        changePasswordDTO.setIp(ip);
        String userAgent = request.getHeader("User-Agent");
        changePasswordDTO.setUserAgent(userAgent);
        String token = getToken(authToken);
        userService.renewPassword(changePasswordDTO,token);
        return JsonResult.ok();
    }
    public String getToken(String authToken){
        if (authToken==null||!(authToken.startsWith("Bearer "))){
            throw new CoolSharkServiceException(ResponseCode.BAD_REQUEST,"无法从请求中拿到token");
        }
        return authToken.substring(tokenHead.length()).trim();
    }

    /**
     * 注册成功返回的用户信息
     */
    @Data
    @ApiModel(value = "注册成功返回的用户信息")
    public static class RegisterUserVO {
        @ApiModelProperty(value = "用户ID")
        private Long id;
        @ApiModelProperty(value = "用户名")
        private String username;
        @ApiModelProperty(value = "昵称")
        private String nickname;

        public RegisterUserVO(Long id, String username, String nickname) {
            this.id = id;
            this.username = username;
            this.nickname = nickname;
        }
    }
}

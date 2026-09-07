package com.cooxiao.mall.pojo.admin.dto;

import com.cooxiao.mall.common.serializer.PhoneDesensitizeSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 后台账号新增 DTO（POST/GET 绑定均可触发校验：Controller 类级 @Validated）
 */
@Data
@ApiModel(value="后台账号新增DTO")
public class AdminAddDTO implements Serializable {

    private static final String MESSAGE_PREFIX = "新增管理员失败，";

    @ApiModelProperty(value="管理员用户名", required = true)
    @NotBlank(message = MESSAGE_PREFIX + "请填写用户名！")
    @Pattern(regexp = "^[a-zA-Z]{1}[0-9a-zA-Z]{3,15}$",
            message = MESSAGE_PREFIX + "用户名必须是由字母、数字组成的4~16字符，且第1个字符必须是字母！")
    private String username;

    @ApiModelProperty(value="管理员昵称")
    @Size(max = 20, message = MESSAGE_PREFIX + "昵称长度不能超过 20 个字符！")
    private String nickname;

    @ApiModelProperty(value="用户密码", required = true)
    @NotBlank(message = MESSAGE_PREFIX + "请填写密码！")
    @Size(min = 4, max = 16, message = MESSAGE_PREFIX + "密码长度必须是 4~16 位！")
    private String password;

    @ApiModelProperty(value="用户确认密码", required = true)
    @NotBlank(message = MESSAGE_PREFIX + "请填写确认密码！")
    @Size(min = 4, max = 16, message = MESSAGE_PREFIX + "确认密码长度必须是 4~16 位！")
    private String passwordAct;

    @ApiModelProperty(value="管理员头像url")
    private String avatar;

    @ApiModelProperty(value="管理员手机号")
    @JsonSerialize(using = PhoneDesensitizeSerializer.class)
    @Pattern(regexp = "^1(?:3\\d|4[4-9]|5[0-35-9]|6[67]|7[013-8]|8\\d|9\\d)\\d{8}$",
            message = MESSAGE_PREFIX + "请输入中国大陆有效手机号！")
    private String phone;

    @ApiModelProperty(value="管理员电子邮箱")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$",
            message = MESSAGE_PREFIX + "请填写正确格式的邮箱！")
    private String email;

    @ApiModelProperty(value="管理员描述")
    @Size(max = 255, message = MESSAGE_PREFIX + "描述长度不能超过 255 个字符！")
    private String description;

    @ApiModelProperty(value="是否启用，1=启用，0=未启用")
    private Integer isEnable;
}

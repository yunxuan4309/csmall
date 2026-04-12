package com.cooxiao.mall.pojo.ums.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户修改密码日志表
 * </p>
 *
 * @author cooxiao.com
 * @since 2022-02-22
 */
@Data
@TableName("ums_change_password_log")
public class ChangePasswordLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户id
     */

    private Long userId;

    /**
     * 用户名（冗余）
     */

    private String username;

    /**
     * 昵称（冗余）
     */

    private String nickname;

    /**
     * 新密码（密文）
     */

    private String newPassword;

    /**
     * IP地址
     */

    private String ip;

    /**
     * 浏览器内核
     */

    private String userAgent;

    /**
     * 修改密码时间
     */

    private LocalDateTime gmtChangePassword;

    /**
     * 数据创建时间
     */

    private LocalDateTime gmtCreate;

    /**
     * 数据最后修改时间
     */

    private LocalDateTime gmtModified;


}

package com.cooxiao.mall.sso.pojo.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("admin_authority") // 添加表名映射
public class AdminAutority implements Serializable, GrantedAuthority {

    @TableId(value = "id", type = IdType.AUTO) // 明确指定主键和生成策略
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 值
     */
    private String value;

    /**
     * 描述
     */
    private String description;

    /**
     * 自定义排序序号
     */
    private Integer sort;

    /**
     * 数据创建时间
     */
    private LocalDateTime gmtCreate;

    /**
     * 数据最后修改时间
     */
    private LocalDateTime gmtModified;

    /**
     * 写入数据
     * @return
     */
    private void setAuthority(String authority){
        this.value=authority;
    }

    @Override
    public String getAuthority() {
        return value;
    }
}

package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 系统用户表 sys_user。 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号（唯一） */
    private String username;
    /** BCrypt 密文 */
    private String password;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    /** 状态：0 停用 / 1 启用 */
    private Integer status;
    /** 最近登录时间 */
    private LocalDateTime loginDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}

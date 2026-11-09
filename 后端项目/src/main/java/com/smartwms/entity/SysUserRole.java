package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 用户-角色关联表 sys_user_role（唯一键 uk_user_role）。 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    private Long id;
    private Long userId;
    private Long roleId;
}

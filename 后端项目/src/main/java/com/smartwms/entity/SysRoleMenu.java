package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 角色-菜单授权表 sys_role_menu（唯一键 uk_role_menu）。 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu {

    private Long id;
    private Long roleId;
    private Long menuId;
}

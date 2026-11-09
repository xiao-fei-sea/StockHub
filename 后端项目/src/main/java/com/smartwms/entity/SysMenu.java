package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 菜单权限表 sys_menu（目录/菜单/按钮三级）。 */
@Data
@TableName("sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentId;
    private String menuName;
    /** 类型：C 目录 / M 菜单 / B 按钮 */
    private String menuType;
    /** 路由路径（C/M） */
    private String path;
    /** 前端组件路径（M） */
    private String component;
    /** 权限标识，如 sys:user:add（按钮/菜单） */
    private String perms;
    private String icon;
    private Integer sort;
    /** 可见性：0 隐藏 / 1 显示 */
    private Integer visible;
    /** 状态：0 停用 / 1 启用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}

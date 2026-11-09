package com.smartwms.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增菜单入参（API-MENU-002）。
 * 条件必填规则由服务端按 menuType 校验：B 必填 perms，M 必填 path/component。
 */
@Data
public class MenuCreateDTO {

    /** 上级菜单 id；根目录传 0 */
    @NotNull(message = "parentId 不能为空")
    private Long parentId;

    /** 菜单名称 */
    @NotBlank(message = "menuName 不能为空")
    @Size(max = 50, message = "menuName 不能超过 50 字符")
    private String menuName;

    /** 类型：C 目录 / M 菜单 / B 按钮 */
    @NotBlank(message = "menuType 不能为空")
    private String menuType;

    /** 权限标识（B 必填，如 sys:user:add） */
    @Size(max = 100, message = "perms 不能超过 100 字符")
    private String perms;

    /** 路由地址（M 必填） */
    @Size(max = 200, message = "path 不能超过 200 字符")
    private String path;

    /** 前端组件路径（M 必填） */
    @Size(max = 200, message = "component 不能超过 200 字符")
    private String component;

    /** 图标 */
    @Size(max = 100, message = "icon 不能超过 100 字符")
    private String icon;

    /** 排序号，默认 0 */
    private Integer sort;

    /** 是否可见：0 隐藏 / 1 显示，默认 1 */
    @Min(value = 0, message = "visible 取值非法")
    @Max(value = 1, message = "visible 取值非法")
    private Integer visible;

    /** 状态：0 停用 / 1 正常，默认 1 */
    @Min(value = 0, message = "status 取值非法")
    @Max(value = 1, message = "status 取值非法")
    private Integer status;
}

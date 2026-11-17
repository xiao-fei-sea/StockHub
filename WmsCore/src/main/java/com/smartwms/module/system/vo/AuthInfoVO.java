package com.smartwms.module.system.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 登录用户信息响应（API-AUTH-004）。 */
@Data
@Builder
public class AuthInfoVO {

    private UserInfoVO user;
    private List<String> roles;
    private List<String> permissions;
    private List<MenuNodeVO> menus;
    private List<WarehouseVO> warehouses;

    @Data
    @Builder
    public static class UserInfoVO {
        private Long id;
        private String username;
        private String realName;
        private String phone;
        private String email;
        private Long deptId;
        private String deptName;
        private Integer status;
        private LocalDateTime loginDate;
    }

    /** 动态菜单树节点（C/M 两级，与前端路由动态加载一致）。 */
    @Data
    @Builder
    public static class MenuNodeVO {
        private Long id;
        private Long parentId;
        private String menuName;
        private String menuType;
        private String path;
        private String component;
        private String icon;
        private Integer sort;
        private Integer visible;
        private List<MenuNodeVO> children;
    }

    @Data
    @Builder
    public static class WarehouseVO {
        private Long id;
        private String warehouseName;
    }
}

package com.smartwms.system.vo;

import lombok.Data;

import java.util.List;

/** 菜单树节点 VO（API-MENU-001），目录/菜单/按钮三层结构。 */
@Data
public class MenuTreeVO {

    private Long id;
    private Long parentId;
    private String menuName;
    /** C 目录 / M 菜单 / B 按钮 */
    private String menuType;
    private String perms;
    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private Integer visible;
    private Integer status;
    private List<MenuTreeVO> children;
}

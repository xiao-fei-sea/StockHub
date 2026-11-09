package com.smartwms.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户列表/详情基础出参（API-USR-001/003）。
 * 不含 password、deleted 等敏感/技术字段（《后端开发规范》第 6.3 条）。
 * createBy/updateBy 按接口文档示例为操作人用户名（库内存 id，服务端反查）。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private String deptName;
    /** 状态：0 停用 / 1 正常 */
    private Integer status;
    private LocalDateTime loginDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 创建人用户名 */
    private String createBy;
    /** 最近更新人用户名 */
    private String updateBy;
}

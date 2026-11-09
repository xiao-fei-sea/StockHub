package com.smartwms.system.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 用户下拉选项出参（API-USR-009）：精简字段，仅启用用户。
 */
@Data
@Builder
public class UserOptionVO {

    private Long id;
    private String username;
    private String realName;
}

package com.smartwms.module.alert.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 处理预警结果出参（API-ALR-007 响应示例）。
 * status 中文标签「已处理」；handleUser 为处理人 ID，handleUserName 为账号；
 * 处理人/时间由服务端写入。
 */
@Data
public class AlertHandleVO {

    private Long id;
    private String status;
    private Long handleUser;
    private String handleUserName;
    private LocalDateTime handleTime;
    private String handleRemark;
}

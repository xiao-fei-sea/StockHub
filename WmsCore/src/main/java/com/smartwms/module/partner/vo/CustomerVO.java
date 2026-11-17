package com.smartwms.module.partner.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 客户详情/列表出参（API-CUS-001/003，无 bankInfo 字段）。 */
@Data
public class CustomerVO {

    private Long id;
    private String customerCode;
    private String customerName;
    private String contactName;
    private String contactPhone;
    private String email;
    private String address;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
}

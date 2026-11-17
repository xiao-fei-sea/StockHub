package com.smartwms.module.partner.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商详情/列表出参（API-SUP-001/003）。
 * bankInfo 展示脱敏（按角色屏蔽——待确认）暂不实现，原值返回。
 */
@Data
public class SupplierVO {

    private Long id;
    private String supplierCode;
    private String supplierName;
    private String contactName;
    private String contactPhone;
    private String email;
    private String address;
    private String bankInfo;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
}

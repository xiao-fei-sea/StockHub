package com.smartwms.warehouse.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 仓库详情/列表出参（API-WH-001/003）。
 * createBy/updateBy 反查为用户名（与接口文档示例 "admin" 口径一致），查不到置 null。
 */
@Data
public class WarehouseVO {

    private Long id;
    private String warehouseCode;
    private String warehouseName;
    private String address;
    private String contactName;
    private String contactPhone;
    private BigDecimal areaSize;
    private Integer status;
    private Integer sort;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
}

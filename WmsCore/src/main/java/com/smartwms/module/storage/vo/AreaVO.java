package com.smartwms.module.storage.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库区详情/列表出参（API-AREA-001）。
 * areaType 返回中文标签；createBy/updateBy 反查为用户名，查不到置 null。
 */
@Data
public class AreaVO {

    private Long id;
    private Long warehouseId;
    private String warehouseName;
    private String areaCode;
    private String areaName;
    /** 库区类型中文标签：存储区/拣货区/暂存区 */
    private String areaType;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
}

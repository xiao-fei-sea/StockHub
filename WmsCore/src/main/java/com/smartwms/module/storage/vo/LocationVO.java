package com.smartwms.module.storage.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 货位详情/列表出参（API-LOC-001）。
 * locationType/currentStatus 返回中文标签；areaName/rackName 关联反查，查不到置 null。
 */
@Data
public class LocationVO {

    private Long id;
    private Long warehouseId;
    private Long areaId;
    private Long rackId;
    private String areaName;
    private String rackName;
    private String locationCode;
    private String locationName;
    /** 货位类型中文标签：存储位/拣货位/暂存位/收货位 */
    private String locationType;
    private BigDecimal capacityVolume;
    /** 货位状态中文标签：空闲/占用/锁定/禁用 */
    private String currentStatus;
    private Integer status;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
}

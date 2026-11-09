package com.smartwms.storage.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 货架详情/列表出参（API-RACK-001）。
 * createBy/updateBy 反查为用户名，查不到置 null。
 */
@Data
public class RackVO {

    private Long id;
    private Long warehouseId;
    private Long areaId;
    private String areaName;
    private String rackCode;
    private String rackName;
    private Integer floorCount;
    private Integer columnCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
}

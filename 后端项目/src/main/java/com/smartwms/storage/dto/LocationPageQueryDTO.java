package com.smartwms.storage.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 货位分页查询入参（API-LOC-001）。
 * 层级参数须自洽：rackId 属于 areaId、areaId 属于 warehouseId，否则 40001。
 * currentStatus/locationType 为中文标签，服务层转 TINYINT 过滤。
 */
@Data
public class LocationPageQueryDTO {

    @Min(value = 1, message = "pageNum 必须大于等于 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize = 20;

    private Long warehouseId;
    private Long areaId;
    private Long rackId;

    /** 启用状态：0 停用 / 1 正常 */
    private Integer status;

    /** 货位状态中文标签：空闲/占用/锁定/禁用（非法标签 40001） */
    private String currentStatus;

    /** 货位类型中文标签：存储位/拣货位/暂存位/收货位（非法标签 40001） */
    private String locationType;

    /** 模糊查询：locationCode / locationName */
    private String keyword;
}

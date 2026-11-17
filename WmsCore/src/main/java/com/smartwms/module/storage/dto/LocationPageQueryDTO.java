package com.smartwms.module.storage.dto;

import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 货位分页查询入参（API-LOC-001）。
 * 层级参数须自洽：rackId 属于 areaId、areaId 属于 warehouseId，否则 40001。
 * currentStatus/locationType 为中文标签，服务层转 TINYINT 过滤。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LocationPageQueryDTO extends PageQueryDTO {

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

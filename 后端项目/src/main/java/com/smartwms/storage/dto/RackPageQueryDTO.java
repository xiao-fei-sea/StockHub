package com.smartwms.storage.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 货架分页查询入参（API-RACK-001）。
 * warehouseId/areaId 组合须匹配（areaId 属于 warehouseId），否则 40001。
 */
@Data
public class RackPageQueryDTO {

    @Min(value = 1, message = "pageNum 必须大于等于 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 必须大于等于 1")
    private Integer pageSize = 20;

    private Long warehouseId;
    private Long areaId;

    /** 模糊查询：rackCode / rackName */
    private String keyword;

    /** 状态过滤：0 停用 / 1 正常 */
    private Integer status;
}

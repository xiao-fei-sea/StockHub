package com.smartwms.module.storage.dto;

import com.smartwms.common.PageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 货架分页查询入参（API-RACK-001）。
 * warehouseId/areaId 组合须匹配（areaId 属于 warehouseId），否则 40001。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RackPageQueryDTO extends PageQueryDTO {

    private Long warehouseId;
    private Long areaId;

    /** 模糊查询：rackCode / rackName */
    private String keyword;

    /** 状态过滤：0 停用 / 1 正常 */
    private Integer status;
}

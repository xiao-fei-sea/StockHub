package com.smartwms.storage.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 货位下拉选项出参（API-LOC-005）：精简字段，不分页，
 * 默认仅返回正常启用且「空闲」的可用货位，最多 500 条。
 */
@Data
@Builder
public class LocationOptionVO {

    private Long id;
    private String locationCode;
    private String locationName;
    /** 货位类型中文标签 */
    private String locationType;
    /** 货位状态中文标签 */
    private String currentStatus;
}

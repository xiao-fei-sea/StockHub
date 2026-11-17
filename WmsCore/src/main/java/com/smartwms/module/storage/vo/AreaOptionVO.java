package com.smartwms.module.storage.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 库区下拉选项出参（API-AREA-005）：精简字段，不分页，默认仅正常库区。
 */
@Data
@Builder
public class AreaOptionVO {

    private Long id;
    private String areaCode;
    private String areaName;
    /** 库区类型中文标签 */
    private String areaType;
}

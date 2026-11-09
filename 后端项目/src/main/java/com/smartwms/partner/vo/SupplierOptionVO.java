package com.smartwms.partner.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 供应商下拉选项出参（API-SUP-005）：精简结构，不分页，
 * 默认仅正常供应商，最多 200 条。
 */
@Data
@Builder
public class SupplierOptionVO {

    private Long id;
    private String supplierCode;
    private String supplierName;
}

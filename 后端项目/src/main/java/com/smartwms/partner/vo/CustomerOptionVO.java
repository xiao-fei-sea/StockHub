package com.smartwms.partner.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 客户下拉选项出参（API-CUS-005）：精简结构，不分页，
 * 默认仅正常客户，最多 200 条。
 */
@Data
@Builder
public class CustomerOptionVO {

    private Long id;
    private String customerCode;
    private String customerName;
}

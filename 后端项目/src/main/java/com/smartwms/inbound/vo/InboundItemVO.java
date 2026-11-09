package com.smartwms.inbound.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入库单明细出参（INB-003 详情 / INB-002 创建响应）。
 * 关联反查 productCode/productName/locationCode，查不到置 null。
 */
@Data
public class InboundItemVO {

    private Long id;
    private Integer lineNo;
    private Long productId;
    private String productCode;
    private String productName;
    private Long locationId;
    private String locationCode;
    private String batchNo;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private BigDecimal planQty;
    private BigDecimal actualQty;
    private String unit;
    private String remark;
}

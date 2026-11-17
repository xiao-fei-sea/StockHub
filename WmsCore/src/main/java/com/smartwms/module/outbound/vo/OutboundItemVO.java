package com.smartwms.module.outbound.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 出库单明细出参（OUT-003 详情 / OUT-002 创建响应）。
 * 关联反查 productCode/productName/locationCode，查不到置 null；
 * batchId/batchNo/locationId 为过账 FIFO 分配后的回填结果（未过账为 null）。
 */
@Data
public class OutboundItemVO {

    private Long id;
    private Integer lineNo;
    private Long productId;
    private String productCode;
    private String productName;
    /** 过账分配后回填 */
    private Long batchId;
    private String batchNo;
    private Long locationId;
    private String locationCode;
    private BigDecimal planQty;
    private BigDecimal actualQty;
    private String unit;
    private String remark;
}

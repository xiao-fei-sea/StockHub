package com.smartwms.module.transfer.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 调拨单明细出参（TRF-003 详情 / TRF-002 创建响应）。
 * 关联反查 productCode/productName/srcLocationCode/destLocationCode/batchNo，查不到置 null；
 * batchNo 由 batchId 反查批次表（明细表无 batch_no 列）；
 * unit 由商品表反查（明细表无 unit 列，以数据库脚本为准）；
 * batchId/srcLocationId 为过账 FIFO 分配后的回填结果（未过账仅保存创建时指定值）。
 */
@Data
public class TransferItemVO {

    private Long id;
    private Integer lineNo;
    private Long productId;
    private String productCode;
    private String productName;
    /** 指定或过账分配后的源仓批次 */
    private Long batchId;
    private String batchNo;
    private Long srcLocationId;
    private String srcLocationCode;
    private Long destLocationId;
    private String destLocationCode;
    private BigDecimal planQty;
    private BigDecimal actualQty;
    private String unit;
    private String remark;
}

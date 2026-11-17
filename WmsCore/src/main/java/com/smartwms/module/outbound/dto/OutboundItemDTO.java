package com.smartwms.module.outbound.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 出库单明细行入参（OUT-002/004 明细结构）。
 * batchNo/locationId 可空（过账 FIFO 分配后回填）；
 * 行内语义校验在服务层：商品存在（40401）、指定批次本仓库存在且正常（40001）、
 * 指定货位属于该仓库（40001）等；创建阶段不校验库存（预检在提交/审核）。
 */
@Data
public class OutboundItemDTO {

    @NotNull(message = "productId 不能为空")
    private Long productId;

    /** 指定批次号（可空；不填过账按 FIFO 自动分配） */
    @Size(max = 50, message = "batchNo 长度不能超过 50 个字符")
    private String batchNo;

    /** 指定出货货位（可空；不填过账按分配批次所在货位回填） */
    private Long locationId;

    /** 计划数量：>0，最多 3 位小数 */
    @NotNull(message = "planQty 不能为空")
    @Positive(message = "planQty 必须大于 0")
    @Digits(integer = 9, fraction = 3, message = "planQty 最多 3 位小数")
    private BigDecimal planQty;

    /** 实发数量；缺省取 planQty */
    @Positive(message = "actualQty 必须大于 0")
    @Digits(integer = 9, fraction = 3, message = "actualQty 最多 3 位小数")
    private BigDecimal actualQty;

    /** 单位；缺省冗余商品表 unit */
    @Size(max = 20, message = "unit 长度不能超过 20 个字符")
    private String unit;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;
}

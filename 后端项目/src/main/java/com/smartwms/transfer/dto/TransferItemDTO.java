package com.smartwms.transfer.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 调拨单明细行入参（TRF-002/004 明细结构）。
 * destLocationId 采纳 13.7 待确认项「建议必填」（须属于目标仓且状态可用，服务层校验 40906）；
 * batchId/srcLocationId 可空（不指定则过账时按源仓 FIFO 分配后回填）；
 * 行内语义校验在服务层；创建阶段不校验源仓可用库存（预检在提交/审核）。
 */
@Data
public class TransferItemDTO {

    @NotNull(message = "productId 不能为空")
    private Long productId;

    /** 指定源仓批次 ID（可空；不填过账按 FIFO 自动分配，调拨沿用源仓批次） */
    private Long batchId;

    /** 指定源货位（可空；不填过账按分配批次所在货位回填） */
    private Long srcLocationId;

    /** 目标货位（必填；须属于 destWarehouseId 且状态非锁定/禁用） */
    @NotNull(message = "destLocationId 不能为空")
    private Long destLocationId;

    /** 计划数量：>0，最多 3 位小数 */
    @NotNull(message = "planQty 不能为空")
    @Positive(message = "planQty 必须大于 0")
    @Digits(integer = 9, fraction = 3, message = "planQty 最多 3 位小数")
    private BigDecimal planQty;

    /** 实际数量；缺省取 planQty（过账扣减口径） */
    @Positive(message = "actualQty 必须大于 0")
    @Digits(integer = 9, fraction = 3, message = "actualQty 最多 3 位小数")
    private BigDecimal actualQty;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;
}

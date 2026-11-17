package com.smartwms.module.inbound.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入库单明细行入参（INB-002/003 明细结构）。
 * 上限 200 行（文档待确认采纳建议）；行内语义校验在服务层：
 * 货位可用（40906）、保质期（40001）、batch_flag=0 时批次置 DEFAULT 等。
 */
@Data
public class InboundItemDTO {

    @NotNull(message = "productId 不能为空")
    private Long productId;

    /** 目标货位（过账写入该货位库存） */
    @NotNull(message = "locationId 不能为空")
    private Long locationId;

    /** 批次号；batch_flag=0 商品忽略并置 DEFAULT */
    @Size(max = 50, message = "batchNo 长度不能超过 50 个字符")
    private String batchNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate productionDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    /** 计划数量：>0，最多 3 位小数 */
    @NotNull(message = "planQty 不能为空")
    @Positive(message = "planQty 必须大于 0")
    @Digits(integer = 9, fraction = 3, message = "planQty 最多 3 位小数")
    private BigDecimal planQty;

    /** 实收数量；缺省取 planQty，过账时必须 >0 */
    @Positive(message = "actualQty 必须大于 0")
    @Digits(integer = 9, fraction = 3, message = "actualQty 最多 3 位小数")
    private BigDecimal actualQty;

    /** 单位；缺省冗余商品表 unit */
    @Size(max = 20, message = "unit 长度不能超过 20 个字符")
    private String unit;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;
}

package com.smartwms.alert.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增/编辑预警规则入参（API-ALR-002/003 同构，规则表无 version 列）。
 * 分类型条件必填在服务层校验（40001）：
 * LOW_STOCK 需 minQty 或 thresholdValue；HIGH_STOCK 需 maxQty 或 thresholdValue；
 * NEAR_EXPIRY 需 nearDays ≥1；EXPIRED 无需阈值；
 * 规则名全局唯一（14.6 待确认采纳「校验唯一」→ 40904）。
 */
@Data
public class AlertRuleSaveDTO {

    @NotBlank(message = "ruleName 不能为空")
    @Size(max = 100, message = "ruleName 长度不能超过 100 个字符")
    private String ruleName;

    /** 适用仓库，空 = 全部仓库（仅不受限角色可不填，服务层校验 40302） */
    private Long warehouseId;

    /** 适用商品分类，空 = 全部分类 */
    private Long categoryId;

    /** 适用商品，空 = 按分类/仓库维度 */
    private Long productId;

    /** 预警类型（英文标签，必填） */
    @NotBlank(message = "alertType 不能为空")
    private String alertType;

    /** 通用阈值（LOW/HIGH 适用，3 位小数） */
    @PositiveOrZero(message = "thresholdValue 不能为负数")
    @Digits(integer = 9, fraction = 3, message = "thresholdValue 最多 3 位小数")
    private BigDecimal thresholdValue;

    /** 临近天数（NEAR_EXPIRY 必填 ≥1，服务层校验） */
    @Min(value = 1, message = "nearDays 至少为 1 天")
    private Integer nearDays;

    /** 库存下限（LOW_STOCK 必填其一，≥0） */
    @PositiveOrZero(message = "minQty 不能为负数")
    @Digits(integer = 9, fraction = 3, message = "minQty 最多 3 位小数")
    private BigDecimal minQty;

    /** 库存上限（HIGH_STOCK 必填其一，≥0） */
    @PositiveOrZero(message = "maxQty 不能为负数")
    @Digits(integer = 9, fraction = 3, message = "maxQty 最多 3 位小数")
    private BigDecimal maxQty;

    /** 规则状态：0停用/1启用，缺省 1 */
    @Min(value = 0, message = "status 仅允许 0 或 1")
    @Max(value = 1, message = "status 仅允许 0 或 1")
    private Integer status;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;
}

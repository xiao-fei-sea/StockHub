package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存预警记录表 stock_alert_record（扫描生成的结果，只增不改库存）。
 * alert_type/level/status 均存 TINYINT（接口层用中文或英文标签，见对应枚举）；
 * 记录表无 version 列（14.4-007：以 status 状态校验幂等）；
 * threshold_value 为生成时的阈值快照（规则后续修改不影响已生成记录）。
 */
@Data
@TableName("stock_alert_record")
public class StockAlertRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源规则 ID（可空，手工/兜底生成时可能无规则） */
    private Long ruleId;
    /** 预警类型：1低于下限/2高于上限/3临近保质期/4已过期 */
    private Integer alertType;
    private Long warehouseId;
    private Long productId;
    /** 关联批次（仅保质期类预警，可空） */
    private Long batchId;
    /** 当前值（当前库存量 / 距过期天数） */
    private BigDecimal currentValue;
    /** 阈值快照 */
    private BigDecimal thresholdValue;
    /** 级别：1提示/2警告/3严重 */
    private Integer level;
    /** 处理状态：0未处理/1已处理 */
    private Integer status;
    private String handleRemark;
    private Long handleUser;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}

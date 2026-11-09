package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存预警规则表 stock_alert_rule（基础配置类，支持逻辑删除；
 * 规则与结果分表，规则只定义阈值条件，由扫描生成 stock_alert_record）。
 * alert_type 存 TINYINT（1-4，接口层用 AlertType 英文标签）；
 * 规则表无 version 列，无乐观锁（以编辑直接覆盖 + 编号唯一校验防重）。
 */
@Data
@TableName("stock_alert_rule")
public class StockAlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleName;
    /** 适用仓库，null = 全部仓库（仅 admin/数据分析员可管理） */
    private Long warehouseId;
    /** 适用分类，null = 全部分类 */
    private Long categoryId;
    /** 适用商品，null = 按分类/仓库维度 */
    private Long productId;
    /** 预警类型：1低于下限/2高于上限/3临近保质期/4已过期 */
    private Integer alertType;
    /** 通用阈值（适用上下限或临近天数） */
    private BigDecimal thresholdValue;
    /** 临近保质期天数（alertType=3 时有效） */
    private Integer nearDays;
    /** 库存下限（alertType=1 时有效） */
    private BigDecimal minQty;
    /** 库存上限（alertType=2 时有效） */
    private BigDecimal maxQty;
    /** 规则状态：0停用/1启用 */
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}

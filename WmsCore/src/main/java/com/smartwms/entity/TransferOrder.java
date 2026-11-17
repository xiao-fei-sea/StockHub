package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 调拨单主表 transfer_order（过账时源仓减、目标仓增的简化模型）。
 * 单据不做逻辑删除（deleted 恒 0，删除接口物理删除草稿）；
 * version 为乐观锁（@Version 由乐观锁插件维护，更新时自动 +1）。
 * 注意：主表无 actual_qty 列（以数据库脚本为准），实际调拨总数由明细汇总；
 * 无 reject_reason 列，驳回原因落 remark（13.7 待确认采纳）。
 */
@Data
@TableName("transfer_order")
public class TransferOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调拨单号（DB+yyyyMMdd+3位序号，唯一） */
    private String transferNo;
    private Long srcWarehouseId;
    private Long destWarehouseId;
    /** 单据状态：0草稿/1待审核/2已审核/3已完成/4已驳回/5已取消 */
    private Integer status;
    /** 目标仓确认状态：0未确认/1已确认（过账置已确认，见 ConfirmStatus 枚举） */
    private Integer confirmStatus;
    private Long auditorId;
    private LocalDateTime auditTime;
    private Long posterId;
    private LocalDateTime postTime;
    /** 计划调拨总数量（明细 planQty 汇总，服务端维护） */
    private BigDecimal totalQty;
    private String remark;

    @Version
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}

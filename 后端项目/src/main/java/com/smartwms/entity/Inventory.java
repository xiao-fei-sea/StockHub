package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存表 inventory（唯一键 warehouse_id+location_id+product_id+batch_id，多仓库隔离强制维度）。
 * 不做逻辑删除；version 乐观锁用于过账并发控制（@Version 自动 +1）。
 */
@Data
@TableName("inventory")
public class Inventory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;
    private Long locationId;
    private Long productId;
    private Long batchId;
    /** 当前库存数量（不得为负） */
    private BigDecimal qty;
    /** 预留锁定数量（默认 0） */
    private BigDecimal lockQty;

    @Version
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}

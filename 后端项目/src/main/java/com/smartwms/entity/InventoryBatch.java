package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存批次表 inventory_batch（唯一键 product_id+warehouse_id+batch_no，
 * 入库过账时创建或复用）。
 */
@Data
@TableName("inventory_batch")
public class InventoryBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 批次号（batch_flag=0 商品统一 DEFAULT） */
    private String batchNo;
    private Long productId;
    private Long warehouseId;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private Long supplierId;
    /** 首次入库单号（期初批次可为空） */
    private String inboundNo;
    /** 批次状态：1正常/2冻结/3用尽 */
    private Integer batchStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}

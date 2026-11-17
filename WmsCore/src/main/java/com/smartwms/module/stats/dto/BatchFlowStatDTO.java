package com.smartwms.module.stats.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * stock_flow 按（仓库, 批次）聚合的最近出入库流水时间（API-STS-006 中间查询结果）。
 * lastInboundTime / lastOutboundTime 无对应方向流水时为 null。
 */
@Data
public class BatchFlowStatDTO {

    private Long warehouseId;
    private Long batchId;
    private LocalDateTime lastInboundTime;
    private LocalDateTime lastOutboundTime;
}

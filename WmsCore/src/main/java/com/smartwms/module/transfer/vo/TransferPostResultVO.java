package com.smartwms.module.transfer.vo;

import com.smartwms.module.inventory.vo.StockFlowVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调拨过账结果出参（API-TRF-009 响应示例）。
 * statusDesc 固定「已完成」、confirmStatus 固定「已确认」；
 * flows 为本次过账生成的库存流水（每条明细两条：源仓 OUT 调拨出 + 目标仓 IN 调拨入）。
 */
@Data
@Builder
public class TransferPostResultVO {

    private Long id;
    private String transferNo;
    private Integer status;
    private String statusDesc;
    private String confirmStatus;
    private Long posterId;
    private LocalDateTime postTime;
    private Integer version;
    private List<StockFlowVO> flows;
}

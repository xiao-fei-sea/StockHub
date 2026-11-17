package com.smartwms.module.stocktake.vo;

import com.smartwms.module.inventory.vo.StockFlowVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点过账结果出参（API-STK-009 响应示例）。
 * diffStatus 固定「已处理」；flows 为盘盈(IN)/盘亏(OUT)流水（flow_type 5/6）。
 */
@Data
@Builder
public class StocktakePostResultVO {

    private Long id;
    private String stocktakeNo;
    private Integer status;
    private String statusDesc;
    private String diffStatus;
    private Long posterId;
    private LocalDateTime postTime;
    private LocalDateTime finishTime;
    private Integer version;
    private List<StockFlowVO> flows;
}

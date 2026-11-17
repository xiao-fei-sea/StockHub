package com.smartwms.module.outbound.vo;

import com.smartwms.module.inventory.vo.StockFlowVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 出库过账结果出参（API-OUT-009 响应示例）。
 * statusDesc 固定「已完成」；flows 为本次过账生成的库存流水（direction=OUT）。
 */
@Data
@Builder
public class OutboundPostResultVO {

    private Long id;
    private String outboundNo;
    private Integer status;
    private String statusDesc;
    private Long posterId;
    private LocalDateTime postTime;
    private Integer version;
    private List<StockFlowVO> flows;
}

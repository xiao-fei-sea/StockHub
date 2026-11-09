package com.smartwms.inbound.vo;

import com.smartwms.inventory.vo.StockFlowVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库过账结果出参（API-INB-008 响应示例）。
 * statusDesc 固定「已完成」；flows 为本次过账生成的库存流水。
 */
@Data
@Builder
public class InboundPostResultVO {

    private Long id;
    private String inboundNo;
    private Integer status;
    private String statusDesc;
    private Long posterId;
    private LocalDateTime postTime;
    private Integer version;
    private List<StockFlowVO> flows;
}

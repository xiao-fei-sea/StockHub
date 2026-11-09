package com.smartwms.alert.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 手动扫描结果出参（API-ALR-008 响应示例）。
 * ruleCount=本次扫描的启用规则数；newRecordCount=新生成记录数；
 * detail 按预警类型分组统计（alertType 为英文标签）。
 */
@Data
@Builder
public class AlertScanVO {

    private LocalDateTime scanTime;
    private Integer ruleCount;
    private Integer newRecordCount;
    private List<ScanDetail> detail;

    /** 分类型统计项 { alertType, count }。 */
    @Data
    @Builder
    public static class ScanDetail {
        private String alertType;
        private Integer count;
    }
}

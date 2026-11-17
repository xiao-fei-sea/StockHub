package com.smartwms.module.storage.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 批量生成货位结果出参（API-LOC-006）。
 */
@Data
@Builder
public class LocationBatchResultVO {

    /** 计划生成总数 = 层数 × 列数 */
    private Integer totalCount;
    /** 实际新建数 */
    private Integer createdCount;
    /** 因编码已存在跳过数（skipExists=true 时生效） */
    private Integer skippedCount;
    /** 首个生成的货位编码 */
    private String firstLocationCode;
    /** 最后生成的货位编码 */
    private String lastLocationCode;
}

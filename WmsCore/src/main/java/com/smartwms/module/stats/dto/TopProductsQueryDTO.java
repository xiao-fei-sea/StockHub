package com.smartwms.module.stats.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 出入库 Top 商品入参（API-STS-005）。
 * limit ∈ [1,50] 默认 10；type ∈ {inbound(默认), outbound, total}；days ∈ [1,365] 默认 30；
 * 显式 startDate/endDate 与 days 互传时以显式日期优先（须成对传入，yyyy-MM-dd）。
 */
@Data
public class TopProductsQueryDTO {

    /** 返回条数（默认 10，取值 1~50） */
    @Min(value = 1, message = "limit 取值范围 1~50")
    @Max(value = 50, message = "limit 取值范围 1~50")
    private Integer limit = 10;

    /** 排序依据：inbound / outbound / total */
    @Pattern(regexp = "inbound|outbound|total", message = "type 仅支持 inbound/outbound/total")
    private String type = "inbound";

    /** 统计天数（默认 30，取值 1~365） */
    @Min(value = 1, message = "days 取值范围 1~365")
    @Max(value = 365, message = "days 取值范围 1~365")
    private Integer days = 30;

    /** 开始日期（yyyy-MM-dd，与 endDate 成对，显式日期优先于 days） */
    private String startDate;

    /** 结束日期（yyyy-MM-dd） */
    private String endDate;

    /** 仓库过滤（空 = 数据权限内全部仓库） */
    private Long warehouseId;
}

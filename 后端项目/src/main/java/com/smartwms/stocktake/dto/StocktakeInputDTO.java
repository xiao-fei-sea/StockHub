package com.smartwms.stocktake.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 录入/更新实盘数入参（API-STK-004）。
 * 仅草稿/已驳回可录入（40901）；可只传有变化的行（按 id 匹配，须属本单 40001）；
 * actualQty ≥ 0、3 位小数；differQty 与 totalDifferQty 服务端重算；录入后状态回 0。
 */
@Data
public class StocktakeInputDTO {

    /** 乐观锁版本号（推荐携带，不一致 40905） */
    private Integer version;

    @Size(max = 500, message = "remark 长度不能超过 500 个字符")
    private String remark;

    @NotEmpty(message = "明细实盘数据不能为空")
    @Size(max = 500, message = "单次录入行数不能超过 500 条")
    @Valid
    private List<StocktakeInputItemDTO> items;

    @Data
    public static class StocktakeInputItemDTO {

        @NotNull(message = "明细 id 不能为空")
        private Long id;

        @NotNull(message = "actualQty 不能为空")
        @PositiveOrZero(message = "actualQty 不能为负")
        @Digits(integer = 9, fraction = 3, message = "actualQty 最多 3 位小数")
        private BigDecimal actualQty;

        @Size(max = 200, message = "diffReason 长度不能超过 200 个字符")
        private String diffReason;

        /** 差异状态中文：差异已确认 / 无需处理（可空，缺省保持原值） */
        private String status;
    }
}

package com.smartwms.common.enums;

import com.smartwms.common.BizException;

/**
 * 库存流水类型（stock_flow.flow_type，TINYINT：1入库/2出库/3调拨入/4调拨出/5盘盈/6盘亏/7调整）。
 * 查询接口（INV-002）入参传中文标签、出参返回中文标签（接口文档口径），服务层转 TINYINT；非法值 40001。
 */
public enum StockFlowType {
    INBOUND("入库", 1),
    OUTBOUND("出库", 2),
    TRANSFER_IN("调拨入", 3),
    TRANSFER_OUT("调拨出", 4),
    STOCKTAKE_GAIN("盘盈", 5),
    STOCKTAKE_LOSS("盘亏", 6),
    ADJUST("调整", 7);

    private final String label;
    private final Integer code;

    StockFlowType(String label, Integer code) {
        this.label = label;
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    /** 中文标签转码值；null/空白返回 null，非法标签抛 40001。 */
    public static Integer toCode(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String trimmed = label.trim();
        for (StockFlowType type : values()) {
            if (type.label.equals(trimmed)) {
                return type.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID,
                "flowType 取值非法，仅允许：入库/出库/调拨入/调拨出/盘盈/盘亏/调整");
    }

    /** 码值转中文标签；未知码值原样转字符串返回。 */
    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (StockFlowType type : values()) {
            if (type.code.equals(code)) {
                return type.label;
            }
        }
        return String.valueOf(code);
    }
}

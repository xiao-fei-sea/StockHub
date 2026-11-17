package com.smartwms.common.enums;

import com.smartwms.common.BizException;

/**
 * 入库单类型（inbound_order.order_type，TINYINT：1采购入库/2调拨入库/3退货入库/4盘盈入库）。
 * 接口传参与返回均为中文枚举名（OpenAPI 明确口径），服务层转 TINYINT 存储；非法值 40001。
 */
public enum InboundOrderType {
    PURCHASE("采购入库", 1),
    TRANSFER_IN("调拨入库", 2),
    RETURN_IN("退货入库", 3),
    STOCKTAKE_GAIN("盘盈入库", 4);

    private final String label;
    private final Integer code;

    InboundOrderType(String label, Integer code) {
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
        for (InboundOrderType type : values()) {
            if (type.label.equals(trimmed)) {
                return type.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID,
                "orderType 取值非法，仅允许：采购入库/调拨入库/退货入库/盘盈入库");
    }

    /** 码值转中文标签；未知码值原样转字符串返回。 */
    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (InboundOrderType type : values()) {
            if (type.code.equals(code)) {
                return type.label;
            }
        }
        return String.valueOf(code);
    }
}

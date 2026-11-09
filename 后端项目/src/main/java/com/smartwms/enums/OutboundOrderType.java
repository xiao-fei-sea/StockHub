package com.smartwms.enums;

import com.smartwms.exception.BizException;

/**
 * 出库单类型（outbound_order.order_type，TINYINT：1销售出库/2调拨出库/3退货出库/4报损出库）。
 * 接口传参与返回均为中文枚举名（OpenAPI 明确口径），服务层转 TINYINT 存储；非法值 40001。
 */
public enum OutboundOrderType {
    SALE("销售出库", 1),
    TRANSFER_OUT("调拨出库", 2),
    RETURN_OUT("退货出库", 3),
    LOSS_OUT("报损出库", 4);

    private final String label;
    private final Integer code;

    OutboundOrderType(String label, Integer code) {
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
        for (OutboundOrderType type : values()) {
            if (type.label.equals(trimmed)) {
                return type.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID,
                "orderType 取值非法，仅允许：销售出库/调拨出库/退货出库/报损出库");
    }

    /** 码值转中文标签；未知码值原样转字符串返回。 */
    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (OutboundOrderType type : values()) {
            if (type.code.equals(code)) {
                return type.label;
            }
        }
        return String.valueOf(code);
    }
}

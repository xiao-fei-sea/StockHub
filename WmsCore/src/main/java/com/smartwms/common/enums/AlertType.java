package com.smartwms.common.enums;

import com.smartwms.common.BizException;

/**
 * 库存预警类型（stock_alert_rule / stock_alert_record.alert_type，TINYINT：
 * 1低于下限LOW_STOCK/2高于上限HIGH_STOCK/3临近保质期NEAR_EXPIRY/4已过期EXPIRED）。
 * 接口层 alertType 用英文枚举标签（LOW_STOCK 等，文档 14.4 查询示例）、
 * alertTypeDesc 用中文描述；服务层存 TINYINT；非法标签返回 40001。
 */
public enum AlertType {
    LOW_STOCK("LOW_STOCK", "低于下限", 1),
    HIGH_STOCK("HIGH_STOCK", "高于上限", 2),
    NEAR_EXPIRY("NEAR_EXPIRY", "临近保质期", 3),
    EXPIRED("EXPIRED", "已过期", 4);

    private final String label;
    private final String desc;
    private final Integer code;

    AlertType(String label, String desc, Integer code) {
        this.label = label;
        this.desc = desc;
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    /** 英文标签转码值；null/空白返回 null，非法标签抛 40001。 */
    public static Integer toCode(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String trimmed = label.trim();
        for (AlertType type : values()) {
            if (type.label.equalsIgnoreCase(trimmed)) {
                return type.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID,
                "alertType 取值非法，仅允许：LOW_STOCK/HIGH_STOCK/NEAR_EXPIRY/EXPIRED");
    }

    /** 码值转中文描述；未知码值原样转字符串返回。 */
    public static String toDesc(Integer code) {
        if (code == null) {
            return null;
        }
        for (AlertType type : values()) {
            if (type.code.equals(code)) {
                return type.desc;
            }
        }
        return String.valueOf(code);
    }

    /** 码值转英文标签；未知码值原样转字符串返回。 */
    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (AlertType type : values()) {
            if (type.code.equals(code)) {
                return type.label;
            }
        }
        return String.valueOf(code);
    }
}

package com.smartwms.common.enums;

import com.smartwms.common.BizException;

/**
 * 预警级别（stock_alert_record.level，TINYINT：1提示/2警告/3严重）。
 * 接口层使用中文标签，服务层转 TINYINT；非法标签返回 40001。
 * 判定规则（14.6 待确认采纳建议）：current_value 与阈值偏差比例
 * ≤30% 提示、≤60% 警告、>60% 严重；EXPIRED 固定严重。
 */
public enum AlertLevel {
    TIP("提示", 1),
    WARNING("警告", 2),
    SEVERE("严重", 3);

    private final String label;
    private final Integer code;

    AlertLevel(String label, Integer code) {
        this.label = label;
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    /** 标签转码值；null/空白返回 null，非法标签抛 40001。 */
    public static Integer toCode(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String trimmed = label.trim();
        for (AlertLevel level : values()) {
            if (level.label.equals(trimmed)) {
                return level.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID, "level 取值非法，仅允许：提示/警告/严重");
    }

    /** 码值转标签；未知码值原样转字符串返回。 */
    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (AlertLevel level : values()) {
            if (level.code.equals(code)) {
                return level.label;
            }
        }
        return String.valueOf(code);
    }
}

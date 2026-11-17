package com.smartwms.common.enums;

import com.smartwms.common.BizException;

/**
 * 预警处理状态（stock_alert_record.status，TINYINT：0未处理/1已处理）。
 * 接口层使用中文标签，服务层转 TINYINT；非法标签返回 40001。
 * 注意与规则状态 status（0停用/1启用，见规则 VO 描述字段）区分。
 */
public enum HandleStatus {
    UNHANDLED("未处理", 0),
    HANDLED("已处理", 1);

    private final String label;
    private final Integer code;

    HandleStatus(String label, Integer code) {
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
        for (HandleStatus status : values()) {
            if (status.label.equals(trimmed)) {
                return status.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID, "status 取值非法，仅允许：未处理/已处理");
    }

    /** 码值转标签；未知码值原样转字符串返回。 */
    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (HandleStatus status : values()) {
            if (status.code.equals(code)) {
                return status.label;
            }
        }
        return String.valueOf(code);
    }
}

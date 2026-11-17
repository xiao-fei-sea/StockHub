package com.smartwms.common.enums;

import com.smartwms.common.BizException;

/**
 * 货位状态（location.current_status，TINYINT：1空闲/2占用/3锁定/4禁用）。
 * 接口层使用中文标签，服务层转 TINYINT；非法标签返回 40001。
 * 注意与启用状态 status（0停用/1正常）区分。
 */
public enum LocationState {
    FREE("空闲", 1),
    OCCUPIED("占用", 2),
    LOCKED("锁定", 3),
    DISABLED("禁用", 4);

    private final String label;
    private final Integer code;

    LocationState(String label, Integer code) {
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
        for (LocationState state : values()) {
            if (state.label.equals(trimmed)) {
                return state.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID,
                "currentStatus 取值非法，仅允许：空闲/占用/锁定/禁用");
    }

    /** 码值转标签；未知码值原样转字符串返回。 */
    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (LocationState state : values()) {
            if (state.code.equals(code)) {
                return state.label;
            }
        }
        return String.valueOf(code);
    }
}

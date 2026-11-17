package com.smartwms.common.enums;

import com.smartwms.common.BizException;

/**
 * 货位类型（location.location_type，TINYINT：1存储位/2拣货位/3暂存位/4收货位）。
 * 接口层使用中文标签，服务层转 TINYINT；非法标签返回 40001。
 */
public enum LocationType {
    STORAGE("存储位", 1),
    PICKING("拣货位", 2),
    STAGING("暂存位", 3),
    RECEIVING("收货位", 4);

    private final String label;
    private final Integer code;

    LocationType(String label, Integer code) {
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
        for (LocationType type : values()) {
            if (type.label.equals(trimmed)) {
                return type.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID,
                "locationType 取值非法，仅允许：存储位/拣货位/暂存位/收货位");
    }

    /** 码值转标签；未知码值原样转字符串返回。 */
    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (LocationType type : values()) {
            if (type.code.equals(code)) {
                return type.label;
            }
        }
        return String.valueOf(code);
    }
}

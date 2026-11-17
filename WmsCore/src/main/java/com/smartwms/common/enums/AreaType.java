package com.smartwms.common.enums;

import com.smartwms.common.BizException;

/**
 * 库区类型（storage_area.area_type，TINYINT：1存储区/2拣货区/3暂存区）。
 * 接口层出入参使用中文标签（接口文档 AREA 系列），服务层负责与码值互转；
 * 非法标签返回 40001。
 */
public enum AreaType {
    STORAGE("存储区", 1),
    PICKING("拣货区", 2),
    STAGING("暂存区", 3);

    private final String label;
    private final Integer code;

    AreaType(String label, Integer code) {
        this.label = label;
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    /** 标签转码值；null/空白返回 null（必填校验由调用方处理），非法标签抛 40001。 */
    public static Integer toCode(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String trimmed = label.trim();
        for (AreaType type : values()) {
            if (type.label.equals(trimmed)) {
                return type.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID, "areaType 取值非法，仅允许：存储区/拣货区/暂存区");
    }

    /** 码值转标签；未知码值原样转字符串返回（不抛异常，避免历史数据导致接口 500）。 */
    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (AreaType type : values()) {
            if (type.code.equals(code)) {
                return type.label;
            }
        }
        return String.valueOf(code);
    }
}

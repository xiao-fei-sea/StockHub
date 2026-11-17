package com.smartwms.common.enums;

import com.smartwms.common.BizException;

/**
 * 盘点范围（stocktake_order.scope，TINYINT：1按库区/2按仓库）。
 * 接口传参与返回为中文枚举名，服务层转 TINYINT；非法值 40001。
 */
public enum StocktakeScope {
    BY_AREA("按库区", 1),
    BY_WAREHOUSE("按仓库", 2);

    private final String label;
    private final Integer code;

    StocktakeScope(String label, Integer code) {
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
        for (StocktakeScope scope : values()) {
            if (scope.label.equals(trimmed)) {
                return scope.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID, "scope 取值非法，仅允许：按库区 / 按仓库");
    }

    /** 码值转中文标签；未知码值原样转字符串返回。 */
    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (StocktakeScope scope : values()) {
            if (scope.code.equals(code)) {
                return scope.label;
            }
        }
        return String.valueOf(code);
    }
}

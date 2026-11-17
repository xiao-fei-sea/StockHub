package com.smartwms.common.enums;

import com.smartwms.common.BizException;

/**
 * 盘点单差异处理状态（stocktake_order.diff_status，TINYINT：0未处理/1已处理）。
 * 查询入参与出参为中文标签，服务层转 TINYINT；非法值 40001。过账后置为已处理。
 */
public enum DiffHandleStatus {
    UNHANDLED("未处理", 0),
    HANDLED("已处理", 1);

    private final String label;
    private final Integer code;

    DiffHandleStatus(String label, Integer code) {
        this.label = label;
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public static Integer toCode(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String trimmed = label.trim();
        for (DiffHandleStatus status : values()) {
            if (status.label.equals(trimmed)) {
                return status.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID, "diffStatus 取值非法，仅允许：未处理 / 已处理");
    }

    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (DiffHandleStatus status : values()) {
            if (status.code.equals(code)) {
                return status.label;
            }
        }
        return String.valueOf(code);
    }
}

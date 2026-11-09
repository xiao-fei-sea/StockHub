package com.smartwms.enums;

import com.smartwms.exception.BizException;

/**
 * 盘点明细差异状态（stocktake_order_item.status，TINYINT：0差异已确认/1无需处理）。
 * 接口传参与返回为中文枚举名，服务层转 TINYINT；非法值 40001。
 */
public enum DiffItemStatus {
    CONFIRMED("差异已确认", 0),
    NO_ACTION("无需处理", 1);

    private final String label;
    private final Integer code;

    DiffItemStatus(String label, Integer code) {
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
        for (DiffItemStatus status : values()) {
            if (status.label.equals(trimmed)) {
                return status.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID, "status 取值非法，仅允许：差异已确认 / 无需处理");
    }

    /** 码值转中文标签；未知码值原样转字符串返回。 */
    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (DiffItemStatus status : values()) {
            if (status.code.equals(code)) {
                return status.label;
            }
        }
        return String.valueOf(code);
    }
}

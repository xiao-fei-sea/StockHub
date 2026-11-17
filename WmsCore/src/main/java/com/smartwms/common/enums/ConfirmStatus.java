package com.smartwms.common.enums;

import com.smartwms.common.BizException;

/**
 * 调拨目标仓确认状态（transfer_order.confirm_status，TINYINT：0未确认/1已确认）。
 * 简化模型（文档 13.5 API-TRF-009）：首期将「目标仓收货确认」合并到过账动作，
 * 过账即置为已确认，仅作展示标记；初始未确认。
 * 13.7 待确认项采纳：未确认 / 已确认，过账置为已确认。
 */
public enum ConfirmStatus {
    UNCONFIRMED("未确认", 0),
    CONFIRMED("已确认", 1);

    private final String label;
    private final Integer code;

    ConfirmStatus(String label, Integer code) {
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
        for (ConfirmStatus status : values()) {
            if (status.label.equals(trimmed)) {
                return status.code;
            }
        }
        throw new BizException(ResultCode.PARAM_INVALID,
                "confirmStatus 取值非法，仅允许：未确认/已确认");
    }

    /** 码值转标签；未知码值原样转字符串返回。 */
    public static String toLabel(Integer code) {
        if (code == null) {
            return null;
        }
        for (ConfirmStatus status : values()) {
            if (status.code.equals(code)) {
                return status.label;
            }
        }
        return String.valueOf(code);
    }
}

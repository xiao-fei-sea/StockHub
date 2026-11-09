package com.smartwms.enums;

/**
 * 单据统一状态机（基线第 7 节，入库/出库/盘点/调拨共用）：
 * 0草稿 → 1待审核 → 2已审核 → 3已完成(已过账)；4已驳回（可撤回至 0）；5已取消（终态）。
 */
public enum OrderStatus {
    DRAFT(0, "草稿"),
    PENDING(1, "待审核"),
    APPROVED(2, "已审核"),
    POSTED(3, "已完成"),
    REJECTED(4, "已驳回"),
    CANCELLED(5, "已取消");

    private final Integer code;
    private final String desc;

    OrderStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /** 码值转状态描述（statusDesc 字段）；未知码值返回空串（不应发生）。 */
    public static String descOf(Integer code) {
        if (code == null) {
            return "";
        }
        for (OrderStatus s : values()) {
            if (s.code.equals(code)) {
                return s.desc;
            }
        }
        return "";
    }
}

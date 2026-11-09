package com.smartwms.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 错误码枚举，与基线第 9 节 /《18. API错误码说明》一一对应。
 * 禁止新增未登记码值（《后端开发规范》第 11 节第 8 条）。
 */
@Getter
@RequiredArgsConstructor
public enum ResultCode {

    // 框架级
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "参数校验失败"),
    UNAUTHORIZED(401, "未认证或令牌已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),

    // 业务级
    PARAM_INVALID(40001, "参数校验失败"),
    USER_PASSWORD_ERROR(40101, "账号或密码错误"),
    USER_DISABLED(40102, "账号已停用"),
    NO_PERM(40301, "无操作权限"),
    NO_WAREHOUSE_PERM(40302, "无数据仓库权限"),
    ORDER_NOT_EXIST(40401, "单据不存在或已删除"),
    ORDER_STATUS_INVALID(40901, "当前单据状态不允许该操作"),
    INSUFFICIENT_STOCK(40902, "库存不足"),
    NEGATIVE_STOCK(40903, "库存不能为负"),
    CODE_EXISTS(40904, "编码已存在"),
    DUPLICATE_SUBMIT(40905, "请勿重复提交"),
    LOCATION_UNAVAILABLE(40906, "货位状态不可用"),
    STOCKTAKE_CONFLICT(40907, "盘点期间存在并发过账冲突"),
    SYSTEM_ERROR(50001, "系统异常");

    private final Integer code;
    private final String message;
}

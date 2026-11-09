package com.smartwms.exception;

import com.smartwms.enums.ResultCode;
import lombok.Getter;

/**
 * 业务异常：业务代码唯一允许抛出的异常类型（《后端开发规范》第 8 节），
 * 由 GlobalExceptionHandler 统一转为 { code, message, data: null } 响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    /** message 覆盖：用于在登记码语义内给出更精确的中文提示（码值不变）。 */
    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }
}

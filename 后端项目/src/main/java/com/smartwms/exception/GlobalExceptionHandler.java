package com.smartwms.exception;

import com.smartwms.enums.ResultCode;
import com.smartwms.vo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理（《后端开发规范》第 8 节）：
 * 业务错误以 body.code 区分；参数校验 400、未认证 401、无权限 403、系统异常 500，其余业务码 HTTP 200。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 参数校验失败（DTO @Valid：请求体为 MethodArgumentNotValidException，查询参数绑定为 BindException）→ 40001，HTTP 400。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(BindException ex) {
        FieldError fe = ex.getBindingResult().getFieldError();
        String msg = fe == null ? ResultCode.PARAM_INVALID.getMessage()
                : fe.getField() + " " + fe.getDefaultMessage();
        return Result.of(ResultCode.PARAM_INVALID.getCode(), msg);
    }

    /** 业务异常：直接取 ResultCode，message 透传；401 → HTTP 401（令牌/刷新失效需跳登录页），其余 HTTP 200（前端以 body.code 为准）。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException ex) {
        ResultCode rc = ex.getResultCode();
        if (rc == ResultCode.SYSTEM_ERROR) {
            log.error("业务处理异常", ex);
        }
        HttpStatus status = rc == ResultCode.UNAUTHORIZED ? HttpStatus.UNAUTHORIZED : HttpStatus.OK;
        return ResponseEntity.status(status).body(Result.of(rc.getCode(), ex.getMessage()));
    }

    /** Spring Security 方法级拒绝 → 40301，HTTP 403。 */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException ex) {
        return Result.of(ResultCode.NO_PERM.getCode(), ResultCode.NO_PERM.getMessage());
    }

    /** 登录凭证错误（Spring Security 抛出，业务侧统一转 40101 语义）。 */
    @ExceptionHandler(BadCredentialsException.class)
    public Result<Void> handleBadCredentials(BadCredentialsException ex) {
        return Result.of(ResultCode.USER_PASSWORD_ERROR.getCode(), ResultCode.USER_PASSWORD_ERROR.getMessage());
    }

    /** 单号/编码唯一冲突 → 40904。 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("唯一约束冲突: {}", ex.getMessage());
        return Result.of(ResultCode.CODE_EXISTS.getCode(), ResultCode.CODE_EXISTS.getMessage());
    }

    /** 其它未捕获异常 → 50001，HTTP 500；记录堆栈，不向前端泄露堆栈/SQL/类名。 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleOther(Exception ex) {
        log.error("系统未预期异常", ex);
        return Result.of(ResultCode.SYSTEM_ERROR.getCode(), ResultCode.SYSTEM_ERROR.getMessage());
    }
}

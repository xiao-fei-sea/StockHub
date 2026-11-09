package com.smartwms.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smartwms.enums.ResultCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统一响应信封（基线第 9 节）：{ code, message, data, timestamp }。
 * data 字段始终序列化（null 时输出 null，与接口文档示例一致）。
 */
@Data
public class Result<T> {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Integer code;
    private String message;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private T data;
    private String timestamp;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return of(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 供 GlobalExceptionHandler / 过滤器构造错误响应（业务代码禁止直接调用，统一抛 BizException）。 */
    public static <T> Result<T> of(Integer code, String message) {
        return of(code, message, null);
    }

    public static <T> Result<T> of(Integer code, String message, T data) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        r.data = data;
        r.timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        return r;
    }
}

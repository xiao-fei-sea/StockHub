package com.smartwms.module.log.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志出参（API-OPL-001 列表项 / API-OPL-002 详情，字段一致）。
 * requestParams/responseResult 与 errorMsg 均为写入时已脱敏、超长截断后的文本。
 */
@Data
@Builder
public class LogVO {

    private Long id;
    /** 业务模块（如：入库管理） */
    private String module;
    /** 操作标题（如：审核入库单） */
    private String title;
    /** 后端方法签名（类#方法） */
    private String method;
    /** HTTP 方法 GET/POST/PUT/DELETE */
    private String requestMethod;
    private String requestUrl;
    /** 请求参数（敏感字段已脱敏） */
    private String requestParams;
    /** 响应结果摘要（脱敏、超长截断） */
    private String responseResult;
    /** 执行结果：0 失败 / 1 成功 */
    private Integer status;
    /** 失败原因/异常摘要 */
    private String errorMsg;
    private String ip;
    private Long userId;
    private String username;
    /** 耗时（毫秒） */
    private Integer costTime;
    private LocalDateTime operTime;
}

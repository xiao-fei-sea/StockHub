package com.smartwms.log.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 操作日志分页查询入参（API-OPL-001）。
 * pageSize ∈ [1,100]（超出返回 40001）；status ∈ {0,1}、requestMethod ∈ {GET,POST,PUT,DELETE}
 * 由注解校验（非法 40001）；日期 yyyy-MM-dd，startDate ≤ endDate 由服务层校验。
 * 可见范围（16.6 待确认采纳）：不受限用户（admin/ROLE_VIEWER）看全部，其余仅看本人日志。
 */
@Data
public class LogPageQueryDTO {

    @Min(value = 1, message = "pageNum 须 ≥ 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 取值范围 1~100")
    @Max(value = 100, message = "pageSize 取值范围 1~100")
    private Integer pageSize = 20;

    /** 业务模块名称，模糊匹配 */
    private String module;

    /** 操作标题，模糊匹配 */
    private String title;

    /** 操作用户名，模糊匹配 */
    private String username;

    /** 执行结果：0 失败 / 1 成功 */
    @Min(value = 0, message = "status 仅支持 0/1")
    @Max(value = 1, message = "status 仅支持 0/1")
    private Integer status;

    /** HTTP 方法 */
    @Pattern(regexp = "GET|POST|PUT|DELETE", message = "requestMethod 仅支持 GET/POST/PUT/DELETE")
    private String requestMethod;

    /** 关键字（匹配标题、请求路径、用户名） */
    private String keyword;

    /** 操作开始日期 yyyy-MM-dd（含 00:00:00） */
    private String startDate;

    /** 操作结束日期 yyyy-MM-dd（含当天） */
    private String endDate;
}

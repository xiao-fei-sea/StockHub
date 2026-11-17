package com.smartwms.module.log.controller;

import com.smartwms.module.log.dto.LogPageQueryDTO;
import com.smartwms.module.log.service.LogQueryService;
import com.smartwms.module.log.vo.LogVO;
import com.smartwms.common.PageResult;
import com.smartwms.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志接口 API-OPL-001~002（详见《16. 操作日志接口文档》）。
 * 只读接口（日志由切面写入，不提供写/删能力），权限统一 sys:log:list（40301）；
 * 可见范围：admin/ROLE_VIEWER 全量，其余仅本人（越权 40302）。
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogQueryService logQueryService;

    /** OPL-001 操作日志分页查询（模块/标题/操作人/状态/HTTP 方法/关键字/日期过滤）。 */
    @GetMapping
    @PreAuthorize("@perm.has('sys:log:list')")
    public Result<PageResult<LogVO>> page(@Valid LogPageQueryDTO dto) {
        return Result.success(logQueryService.page(dto));
    }

    /** OPL-002 操作日志详情（不存在 40401，越权 40302）。 */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('sys:log:list')")
    public Result<LogVO> detail(@PathVariable Long id) {
        return Result.success(logQueryService.detail(id));
    }
}

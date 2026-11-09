package com.smartwms.alert.controller;

import com.smartwms.alert.dto.AlertHandleDTO;
import com.smartwms.alert.dto.AlertRecordPageQueryDTO;
import com.smartwms.alert.dto.AlertScanDTO;
import com.smartwms.alert.service.AlertService;
import com.smartwms.alert.vo.AlertHandleVO;
import com.smartwms.alert.vo.AlertRecordVO;
import com.smartwms.alert.vo.AlertScanVO;
import com.smartwms.aspect.OperationLog;
import com.smartwms.vo.PageResult;
import com.smartwms.vo.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预警记录接口 API-ALR-006~008（详见《14. 库存预警接口文档》）。
 * perms：006 查询 sys:alert:list、007 处理 sys:alert:handle、008 扫描 sys:alert:scan（40301）；
 * 处理幂等以 status 状态校验（记录表无 version 列，重复处理 40901）；
 * 扫描只新增记录，不修改库存、不拦截业务。
 */
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    /** ALR-006 预警记录分页查询（类型/状态/级别/仓库/商品/日期过滤）。 */
    @GetMapping
    @PreAuthorize("@perm.has('sys:alert:list')")
    public Result<PageResult<AlertRecordVO>> page(@Valid AlertRecordPageQueryDTO dto) {
        return Result.success(alertService.page(dto));
    }

    /** ALR-007 处理预警（仅未处理可处理，重复 40901；处理人/时间服务端写入）。 */
    @PutMapping("/{id}/handle")
    @PreAuthorize("@perm.has('sys:alert:handle')")
    @OperationLog(module = "库存预警", title = "处理预警记录")
    public Result<AlertHandleVO> handle(@PathVariable Long id, @RequestBody @Valid AlertHandleDTO dto) {
        return Result.success(alertService.handle(id, dto));
    }

    /** ALR-008 手动触发扫描（按启用规则生成记录，按日去重，进程内串行）。 */
    @PostMapping("/scan")
    @PreAuthorize("@perm.has('sys:alert:scan')")
    @OperationLog(module = "库存预警", title = "手动触发预警扫描")
    public Result<AlertScanVO> scan(@RequestBody(required = false) @Valid AlertScanDTO dto) {
        if (dto == null) {
            dto = new AlertScanDTO(); // Body 可为空对象
        }
        return Result.success(alertService.scan(dto));
    }
}

package com.smartwms.module.alert.controller;

import com.smartwms.module.alert.dto.AlertRulePageQueryDTO;
import com.smartwms.module.alert.dto.AlertRuleSaveDTO;
import com.smartwms.module.alert.service.AlertRuleService;
import com.smartwms.module.alert.vo.AlertRuleVO;
import com.smartwms.framework.aspect.OperationLog;
import com.smartwms.common.PageResult;
import com.smartwms.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预警规则接口 API-ALR-001~005（详见《14. 库存预警接口文档》）。
 * 查询 perms = sys:alert:rule，写操作 sys:alert:add/edit/delete（40301）；
 * 规则表逻辑删除、无乐观锁（无 version 字段）；
 * 数据权限：warehouse_id 为空（全部仓库）的规则仅不受限角色可管理（40302）。
 */
@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    /** ALR-001 预警规则分页查询（规则名称模糊 + 类型/状态/仓库过滤）。 */
    @GetMapping
    @PreAuthorize("@perm.has('sys:alert:rule')")
    public Result<PageResult<AlertRuleVO>> page(@Valid AlertRulePageQueryDTO dto) {
        return Result.success(alertRuleService.page(dto));
    }

    /** ALR-002 创建预警规则（分类型条件必填 40001、规则名唯一 40904）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:alert:add')")
    @OperationLog(module = "库存预警", title = "新增预警规则")
    public Result<AlertRuleVO> create(@RequestBody @Valid AlertRuleSaveDTO dto) {
        return Result.success(alertRuleService.create(dto));
    }

    /** ALR-003 编辑预警规则（修改仅影响后续扫描，已生成记录保留阈值快照）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:alert:edit')")
    @OperationLog(module = "库存预警", title = "编辑预警规则")
    public Result<AlertRuleVO> update(@PathVariable Long id, @RequestBody @Valid AlertRuleSaveDTO dto) {
        return Result.success(alertRuleService.update(id, dto));
    }

    /** ALR-004 删除预警规则（逻辑删除，已生成记录保留）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:alert:delete')")
    @OperationLog(module = "库存预警", title = "删除预警规则")
    public Result<Void> delete(@PathVariable Long id) {
        alertRuleService.delete(id);
        return Result.success();
    }

    /** ALR-005 预警规则详情（40401/40302）。 */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('sys:alert:rule')")
    public Result<AlertRuleVO> detail(@PathVariable Long id) {
        return Result.success(alertRuleService.detail(id));
    }
}

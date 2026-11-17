package com.smartwms.module.storage.controller;

import com.smartwms.framework.aspect.OperationLog;
import com.smartwms.module.storage.dto.RackCreateDTO;
import com.smartwms.module.storage.dto.RackPageQueryDTO;
import com.smartwms.module.storage.dto.RackUpdateDTO;
import com.smartwms.module.storage.service.RackService;
import com.smartwms.module.storage.vo.RackVO;
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
 * 货架管理接口 API-RACK-001~004（详见《库位管理接口文档》）。
 * 全部需 JWT 认证；查询类无按钮权限（结果按数据仓库权限过滤），
 * 写操作按 sys:rack:* 校验。
 */
@RestController
@RequestMapping("/api/racks")
@RequiredArgsConstructor
public class RackController {

    private final RackService rackService;

    /** RACK-001 货架分页查询（warehouseId/areaId 组合须匹配 40001）。 */
    @GetMapping
    public Result<PageResult<RackVO>> page(@Valid RackPageQueryDTO dto) {
        return Result.success(rackService.page(dto));
    }

    /** RACK-002 新增货架（同库区编码唯一，层数/列数 ≥1 用于货位批量生成）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:rack:add')")
    @OperationLog(module = "货架管理", title = "新增货架")
    public Result<Long> create(@RequestBody @Valid RackCreateDTO dto) {
        return Result.success(rackService.create(dto));
    }

    /** RACK-003 修改货架（已有货位禁止跨库区/缩容 40901）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:rack:edit')")
    @OperationLog(module = "货架管理", title = "修改货架")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid RackUpdateDTO dto) {
        rackService.update(id, dto);
        return Result.success();
    }

    /** RACK-004 删除货架（存在货位/库存引用时 40901，逻辑删除）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:rack:delete')")
    @OperationLog(module = "货架管理", title = "删除货架")
    public Result<Void> delete(@PathVariable Long id) {
        rackService.delete(id);
        return Result.success();
    }
}

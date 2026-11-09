package com.smartwms.outbound.controller;

import com.smartwms.aspect.OperationLog;
import com.smartwms.inbound.dto.OrderActionDTO;
import com.smartwms.outbound.dto.OutboundCreateDTO;
import com.smartwms.outbound.dto.OutboundPageQueryDTO;
import com.smartwms.outbound.dto.OutboundUpdateDTO;
import com.smartwms.outbound.service.OutboundService;
import com.smartwms.outbound.vo.OutboundOrderVO;
import com.smartwms.outbound.vo.OutboundPostResultVO;
import com.smartwms.vo.PageResult;
import com.smartwms.vo.Result;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 出库管理接口 API-OUT-001~011（详见《10. 出库管理接口文档》）。
 * 全部需 JWT 认证并叠加数据仓库权限（40302）；查询需 sys:outbound:list（与入库仅需登录不同）；
 * 写操作按 sys:outbound:* 校验；乐观锁 version 不一致 40905。
 * 动作接口与入库差异：方法为 PUT（入库为 POST）。
 */
@RestController
@RequestMapping("/api/outbounds")
@RequiredArgsConstructor
public class OutboundController {

    private final OutboundService outboundService;

    /** OUT-001 出库单分页查询（默认创建时间倒序）。 */
    @GetMapping
    @PreAuthorize("@perm.has('sys:outbound:list')")
    public Result<PageResult<OutboundOrderVO>> page(@Valid OutboundPageQueryDTO dto) {
        return Result.success(outboundService.page(dto));
    }

    /** OUT-002 新增出库单（CK 单号自动生成，初始草稿，不校验库存）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:outbound:add')")
    @OperationLog(module = "出库管理", title = "新增出库单")
    public Result<OutboundOrderVO> create(@RequestBody @Valid OutboundCreateDTO dto) {
        return Result.success(outboundService.create(dto));
    }

    /** OUT-003 出库单详情（含明细与 FIFO 回填结果）。 */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('sys:outbound:list')")
    public Result<OutboundOrderVO> detail(@PathVariable Long id) {
        return Result.success(outboundService.detail(id));
    }

    /** OUT-004 修改出库单（仅草稿/已驳回，整体替换明细）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:outbound:edit')")
    @OperationLog(module = "出库管理", title = "修改出库单")
    public Result<OutboundOrderVO> update(@PathVariable Long id, @RequestBody @Valid OutboundUpdateDTO dto) {
        return Result.success(outboundService.update(id, dto));
    }

    /** OUT-005 删除出库单（仅草稿 0，物理删除；version 可选 ?version= 乐观锁）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:outbound:delete')")
    @OperationLog(module = "出库管理", title = "删除出库单")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestParam(required = false) Integer version) {
        outboundService.delete(id, version);
        return Result.success();
    }

    /** OUT-006 提交审核（0→1，完整性预检 + 可用库存预检 40902）。 */
    @PutMapping("/{id}/submit")
    @PreAuthorize("@perm.has('sys:outbound:submit')")
    @OperationLog(module = "出库管理", title = "提交出库单")
    public Result<OutboundOrderVO> submit(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(outboundService.submit(id, dto));
    }

    /** OUT-007 审核通过（1→2，含可用库存预检，不改库存）。 */
    @PutMapping("/{id}/audit")
    @PreAuthorize("@perm.has('sys:outbound:audit')")
    @OperationLog(module = "出库管理", title = "审核出库单")
    public Result<OutboundOrderVO> audit(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(outboundService.audit(id, dto));
    }

    /** OUT-008 驳回（1→4，reason 必填，存入 remark）。 */
    @PutMapping("/{id}/reject")
    @PreAuthorize("@perm.has('sys:outbound:reject')")
    @OperationLog(module = "出库管理", title = "驳回出库单")
    public Result<OutboundOrderVO> reject(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(outboundService.reject(id, dto));
    }

    /** OUT-009 过账（2→3，FIFO 分配批次、扣减库存、生成 OUT 流水，整体回滚）。 */
    @PutMapping("/{id}/post")
    @PreAuthorize("@perm.has('sys:outbound:post')")
    @OperationLog(module = "出库管理", title = "出库单过账")
    public Result<OutboundPostResultVO> post(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(outboundService.post(id, dto));
    }

    /** OUT-010 取消（0/1/4→5 终态）。 */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("@perm.has('sys:outbound:cancel')")
    @OperationLog(module = "出库管理", title = "取消出库单")
    public Result<OutboundOrderVO> cancel(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(outboundService.cancel(id, dto));
    }

    /** OUT-011 撤回至草稿（1/4→0）。 */
    @PutMapping("/{id}/withdraw")
    @PreAuthorize("@perm.has('sys:outbound:withdraw')")
    @OperationLog(module = "出库管理", title = "撤回出库单")
    public Result<OutboundOrderVO> withdraw(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(outboundService.withdraw(id, dto));
    }
}

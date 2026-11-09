package com.smartwms.transfer.controller;

import com.smartwms.aspect.OperationLog;
import com.smartwms.inbound.dto.OrderActionDTO;
import com.smartwms.transfer.dto.TransferCreateDTO;
import com.smartwms.transfer.dto.TransferPageQueryDTO;
import com.smartwms.transfer.dto.TransferUpdateDTO;
import com.smartwms.transfer.service.TransferService;
import com.smartwms.transfer.vo.TransferOrderVO;
import com.smartwms.transfer.vo.TransferPostResultVO;
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
 * 库存调拨接口 API-TRF-001~011（详见《13. 库存调拨接口文档》）。
 * 全部需 JWT 认证；查询数据权限为源/目标仓任一可见（40302），
 * 写操作双仓校验（创建/修改/删除/动作/过账均需源仓与目标仓权限）；
 * 查询 perms = sys:transfer:list，写操作按 sys:transfer:* 校验；
 * 乐观锁 version 不一致 40905；动作方法为 PUT（同出库/盘点）。
 */
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    /** TRF-001 调拨单分页查询（默认创建时间倒序，双仓任一权限过滤）。 */
    @GetMapping
    @PreAuthorize("@perm.has('sys:transfer:list')")
    public Result<PageResult<TransferOrderVO>> page(@Valid TransferPageQueryDTO dto) {
        return Result.success(transferService.page(dto));
    }

    /** TRF-002 新增调拨单（DB 单号自动生成，初始草稿/未确认，不校验库存）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:transfer:add')")
    @OperationLog(module = "库存调拨", title = "新增调拨单")
    public Result<TransferOrderVO> create(@RequestBody @Valid TransferCreateDTO dto) {
        return Result.success(transferService.create(dto));
    }

    /** TRF-003 调拨单详情（含明细与过账回填的批次/源货位）。 */
    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('sys:transfer:list')")
    public Result<TransferOrderVO> detail(@PathVariable Long id) {
        return Result.success(transferService.detail(id));
    }

    /** TRF-004 修改调拨单（仅草稿/已驳回，整体替换明细）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:transfer:edit')")
    @OperationLog(module = "库存调拨", title = "修改调拨单")
    public Result<TransferOrderVO> update(@PathVariable Long id, @RequestBody @Valid TransferUpdateDTO dto) {
        return Result.success(transferService.update(id, dto));
    }

    /** TRF-005 删除调拨单（仅草稿 0，物理删除；version 可选 ?version= 乐观锁）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:transfer:delete')")
    @OperationLog(module = "库存调拨", title = "删除调拨单")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestParam(required = false) Integer version) {
        transferService.delete(id, version);
        return Result.success();
    }

    /** TRF-006 提交审核（0→1，完整性预检 + 源仓可用库存预检 40902）。 */
    @PutMapping("/{id}/submit")
    @PreAuthorize("@perm.has('sys:transfer:submit')")
    @OperationLog(module = "库存调拨", title = "提交调拨单")
    public Result<TransferOrderVO> submit(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(transferService.submit(id, dto));
    }

    /** TRF-007 审核通过（1→2，含源仓可用库存预检与双仓权限，不改库存）。 */
    @PutMapping("/{id}/audit")
    @PreAuthorize("@perm.has('sys:transfer:audit')")
    @OperationLog(module = "库存调拨", title = "审核调拨单")
    public Result<TransferOrderVO> audit(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(transferService.audit(id, dto));
    }

    /** TRF-008 驳回（1→4，reason 必填，存入 remark）。 */
    @PutMapping("/{id}/reject")
    @PreAuthorize("@perm.has('sys:transfer:reject')")
    @OperationLog(module = "库存调拨", title = "驳回调拨单")
    public Result<TransferOrderVO> reject(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(transferService.reject(id, dto));
    }

    /** TRF-009 过账（2→3，同事务源仓减 + 目标仓增、双流水、confirm_status 置已确认，整体回滚）。 */
    @PutMapping("/{id}/post")
    @PreAuthorize("@perm.has('sys:transfer:post')")
    @OperationLog(module = "库存调拨", title = "调拨单过账")
    public Result<TransferPostResultVO> post(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(transferService.post(id, dto));
    }

    /** TRF-010 取消（0/1/4→5 终态）。 */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("@perm.has('sys:transfer:cancel')")
    @OperationLog(module = "库存调拨", title = "取消调拨单")
    public Result<TransferOrderVO> cancel(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(transferService.cancel(id, dto));
    }

    /** TRF-011 撤回至草稿（1/4→0）。 */
    @PutMapping("/{id}/withdraw")
    @PreAuthorize("@perm.has('sys:transfer:withdraw')")
    @OperationLog(module = "库存调拨", title = "撤回调拨单")
    public Result<TransferOrderVO> withdraw(@PathVariable Long id, @RequestBody @Valid OrderActionDTO dto) {
        return Result.success(transferService.withdraw(id, dto));
    }
}

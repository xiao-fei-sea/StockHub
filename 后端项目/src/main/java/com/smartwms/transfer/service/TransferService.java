package com.smartwms.transfer.service;

import com.smartwms.inbound.dto.OrderActionDTO;
import com.smartwms.transfer.dto.TransferCreateDTO;
import com.smartwms.transfer.dto.TransferPageQueryDTO;
import com.smartwms.transfer.dto.TransferUpdateDTO;
import com.smartwms.transfer.vo.TransferOrderVO;
import com.smartwms.transfer.vo.TransferPostResultVO;
import com.smartwms.vo.PageResult;

/**
 * 库存调拨服务（API-TRF-001~011）。
 * 单据状态机（OrderStatus）：0草稿→1待审核→2已审核→3已完成；4已驳回（可撤回）；5已取消（终态）。
 * 与出库同构，差异：
 * 1. 过账为源仓减 + 目标仓增的同事务简化模型（13.5），每明细两条流水（调拨出4/调拨入3），
 *    过账后 confirm_status 置已确认；
 * 2. 数据权限：查询可见口径 = 源仓或目标仓任一在权限内；写操作双仓校验（13.7 待确认采纳）。
 * 三重幂等防护：状态机 40901 + 单号唯一 40904 + 乐观锁 40905。
 */
public interface TransferService {

    /** TRF-001 调拨单分页查询（默认创建时间倒序，双仓任一权限过滤）。 */
    PageResult<TransferOrderVO> page(TransferPageQueryDTO dto);

    /** TRF-002 新增调拨单（DB 单号自动生成，初始草稿/未确认，不校验库存），返回含明细。 */
    TransferOrderVO create(TransferCreateDTO dto);

    /** TRF-003 调拨单详情（含明细与过账回填批次/源货位，40401 单据不存在）。 */
    TransferOrderVO detail(Long id);

    /** TRF-004 修改调拨单（仅草稿/已驳回 40901，驳回→草稿，整体替换明细，40905）。 */
    TransferOrderVO update(Long id, TransferUpdateDTO dto);

    /** TRF-005 删除调拨单（仅草稿 0 40901，物理删除主表+明细，version 可选 40905）。 */
    void delete(Long id, Integer version);

    /** TRF-006 提交（0→1，完整性预检 40001 + 源仓可用库存预检 40902）。 */
    TransferOrderVO submit(Long id, OrderActionDTO dto);

    /** TRF-007 审核（1→2，记录审核人/时间 + 源仓可用库存预检 40902，双仓权限）。 */
    TransferOrderVO audit(Long id, OrderActionDTO dto);

    /** TRF-008 驳回（1→4，reason 必填 40001，存入 remark）。 */
    TransferOrderVO reject(Long id, OrderActionDTO dto);

    /** TRF-009 过账（2→3，事务内源仓减 + 目标仓增、每明细双流水、confirm_status 置已确认，返回 flows）。 */
    TransferPostResultVO post(Long id, OrderActionDTO dto);

    /** TRF-010 取消（0/1/4→5 终态）。 */
    TransferOrderVO cancel(Long id, OrderActionDTO dto);

    /** TRF-011 撤回（1/4→0）。 */
    TransferOrderVO withdraw(Long id, OrderActionDTO dto);
}

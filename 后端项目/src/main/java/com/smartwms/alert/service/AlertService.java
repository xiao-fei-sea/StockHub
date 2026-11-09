package com.smartwms.alert.service;

import com.smartwms.alert.dto.AlertHandleDTO;
import com.smartwms.alert.dto.AlertRecordPageQueryDTO;
import com.smartwms.alert.dto.AlertScanDTO;
import com.smartwms.alert.vo.AlertHandleVO;
import com.smartwms.alert.vo.AlertRecordVO;
import com.smartwms.alert.vo.AlertScanVO;
import com.smartwms.vo.PageResult;

/**
 * 预警记录服务（API-ALR-006~008）。
 * 预警为提示性能力：扫描只新增记录，不修改库存、不拦截出入库业务（基线第 8 节）。
 */
public interface AlertService {

    /** ALR-006 预警记录分页查询（类型/状态/级别/仓库/商品/日期过滤，按仓库数据权限）。 */
    PageResult<AlertRecordVO> page(AlertRecordPageQueryDTO dto);

    /** ALR-007 处理预警（仅未处理可处理，重复 40901；处理人/时间服务端写入）。 */
    AlertHandleVO handle(Long id, AlertHandleDTO dto);

    /** ALR-008 手动触发扫描（与每日定时扫描同逻辑，按启用规则生成记录并按日去重）。 */
    AlertScanVO scan(AlertScanDTO dto);
}

package com.smartwms.alert.service;

import com.smartwms.alert.dto.AlertRulePageQueryDTO;
import com.smartwms.alert.dto.AlertRuleSaveDTO;
import com.smartwms.alert.vo.AlertRuleVO;
import com.smartwms.vo.PageResult;

/**
 * 预警规则服务（API-ALR-001~005，规则表逻辑删除、无乐观锁）。
 * 数据权限：warehouse_id 非空按用户权限过滤；warehouse_id 为空（全部仓库）
 * 的规则仅不受限角色（admin/数据分析员）可见、可管理，否则 40302。
 * 规则只定义阈值，修改仅影响后续扫描，已生成记录保留阈值快照。
 */
public interface AlertRuleService {

    /** ALR-001 规则分页查询（规则名称模糊 + 类型/状态/仓库过滤）。 */
    PageResult<AlertRuleVO> page(AlertRulePageQueryDTO dto);

    /** ALR-002 创建规则（分类型条件必填 40001、规则名唯一 40904、引用有效性 40401）。 */
    AlertRuleVO create(AlertRuleSaveDTO dto);

    /** ALR-003 编辑规则（40401，字段校验同创建，修改仅影响后续扫描）。 */
    AlertRuleVO update(Long id, AlertRuleSaveDTO dto);

    /** ALR-004 删除规则（逻辑删除 40401/40302，已生成记录保留）。 */
    void delete(Long id);

    /** ALR-005 规则详情（40401/40302）。 */
    AlertRuleVO detail(Long id);
}

package com.smartwms.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.alert.dto.AlertRulePageQueryDTO;
import com.smartwms.alert.dto.AlertRuleSaveDTO;
import com.smartwms.alert.mapper.StockAlertRuleMapper;
import com.smartwms.alert.service.AlertRuleService;
import com.smartwms.alert.vo.AlertRuleVO;
import com.smartwms.base.mapper.ProductCategoryMapper;
import com.smartwms.base.mapper.ProductMapper;
import com.smartwms.base.mapper.WarehouseMapper;
import com.smartwms.entity.Product;
import com.smartwms.entity.ProductCategory;
import com.smartwms.entity.StockAlertRule;
import com.smartwms.entity.SysUser;
import com.smartwms.entity.Warehouse;
import com.smartwms.enums.AlertType;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.security.LoginUser;
import com.smartwms.security.WarehouseDataPerm;
import com.smartwms.system.mapper.SysUserMapper;
import com.smartwms.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * 预警规则服务实现（API-ALR-001~005）。
 * 14.6 待确认项采纳：
 * 1. rule_name 全局唯一（创建/编辑校验 → 40904，逻辑删除后可复用）；
 * 2. LOW_STOCK 阈值以规则值为准（minQty 优先，缺省取 thresholdValue，不回退商品 min_stock）。
 * 数据权限：warehouse_id 为空（全部仓库）的规则仅不受限角色可管理（否则 40302）。
 * 规则表逻辑删除（MP 全局 deleted 配置），无 version 列（直接覆盖更新）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements AlertRuleService {

    private final StockAlertRuleMapper ruleMapper;
    private final WarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final SysUserMapper userMapper;
    private final WarehouseDataPerm dataPerm;

    // ==================== ALR-001 分页 ====================

    @Override
    public PageResult<AlertRuleVO> page(AlertRulePageQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : Math.min(dto.getPageSize(), 100);
        Integer alertType = AlertType.toCode(dto.getAlertType()); // 非法标签 40001
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "status 取值为 0（停用）或 1（启用）");
        }
        // 指定仓库越权校验（全部仓库 null 不校验）
        dataPerm.checkWarehouse(dto.getWarehouseId());

        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(0L, pageNum, pageSize, List.of());
        }

        LambdaQueryWrapper<StockAlertRule> wrapper = new LambdaQueryWrapper<StockAlertRule>()
                .eq(dto.getWarehouseId() != null, StockAlertRule::getWarehouseId, dto.getWarehouseId())
                .eq(alertType != null, StockAlertRule::getAlertType, alertType)
                .eq(dto.getStatus() != null, StockAlertRule::getStatus, dto.getStatus())
                // 受限用户仅见权限内仓库的规则；warehouse_id 为空（全部仓库）的规则
                // 仅不受限角色可见（SQL IN 天然排除 NULL 行）
                .in(allowed != null, StockAlertRule::getWarehouseId, allowed)
                .orderByDesc(StockAlertRule::getCreateTime)
                .orderByDesc(StockAlertRule::getId);
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.like(StockAlertRule::getRuleName, dto.getKeyword().trim());
        }

        Page<StockAlertRule> page = ruleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<AlertRuleVO> list = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(page.getTotal(), pageNum, pageSize, list);
    }

    // ==================== ALR-002 创建 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleVO create(AlertRuleSaveDTO dto) {
        checkScopePerm(dto.getWarehouseId()); // 全部仓库规则仅不受限角色可建（40302）
        Integer alertType = validate(dto, null); // 分类型条件必填/取值关系 40001；返回码值
        checkRuleNameUnique(dto.getRuleName(), null); // 40904
        checkReferences(dto); // 仓库/分类/商品有效性 40401

        StockAlertRule rule = new StockAlertRule();
        applyDto(rule, dto, alertType);
        rule.setCreateBy(currentUserId());
        ruleMapper.insert(rule);

        // create_time 由 DB 默认值生成（MP NOT_NULL 字段策略不回插），回读取完整实体
        rule = ruleMapper.selectById(rule.getId());
        log.info("新增预警规则成功 id={}, ruleName={}, alertType={}", rule.getId(), rule.getRuleName(), alertType);
        return toVO(rule);
    }

    // ==================== ALR-003 编辑 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleVO update(Long id, AlertRuleSaveDTO dto) {
        StockAlertRule rule = requireRule(id); // 40401
        checkRulePerm(rule); // 原规则数据权限 40302
        checkScopePerm(dto.getWarehouseId()); // 新范围数据权限
        Integer alertType = validate(dto, id);
        checkRuleNameUnique(dto.getRuleName(), id);
        checkReferences(dto);

        applyDto(rule, dto, alertType);
        rule.setUpdateBy(currentUserId());
        ruleMapper.updateById(rule);
        // update_time 由 DB ON UPDATE 生成，回读取完整实体
        rule = ruleMapper.selectById(id);
        log.info("编辑预警规则成功 id={}, ruleName={}", id, rule.getRuleName());
        return toVO(rule);
    }

    // ==================== ALR-004 删除（逻辑） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        StockAlertRule rule = requireRule(id); // 40401
        checkRulePerm(rule);
        // 逻辑删除（deleted=1，MP 全局配置）；已生成的预警记录保留
        ruleMapper.deleteById(id);
        log.info("删除预警规则成功 id={}, ruleName={}", id, rule.getRuleName());
    }

    // ==================== ALR-005 详情 ====================

    @Override
    public AlertRuleVO detail(Long id) {
        StockAlertRule rule = requireRule(id); // 40401
        checkRulePerm(rule);
        return toVO(rule);
    }

    // ==================== 私有方法 ====================

    /** 规则必须存在（含未删除），否则 40401。 */
    private StockAlertRule requireRule(Long id) {
        StockAlertRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "预警规则不存在或已删除");
        }
        return rule;
    }

    /** 规则数据权限：warehouse_id 为空（全部仓库）仅不受限角色可管理，否则 40302。 */
    private void checkRulePerm(StockAlertRule rule) {
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed == null) {
            return;
        }
        if (rule.getWarehouseId() == null || !allowed.contains(rule.getWarehouseId())) {
            throw new BizException(ResultCode.NO_WAREHOUSE_PERM, "无数据仓库权限");
        }
    }

    /** 范围数据权限：不限定仓库（null = 全部仓库）的规则仅不受限角色可创建/修改。 */
    private void checkScopePerm(Long warehouseId) {
        if (warehouseId == null) {
            Set<Long> allowed = dataPerm.allowedWarehouseIds();
            if (allowed != null) {
                throw new BizException(ResultCode.NO_WAREHOUSE_PERM,
                        "全部仓库预警规则仅管理员可维护");
            }
        } else {
            dataPerm.checkWarehouse(warehouseId); // 越权 40302
        }
    }

    /**
     * 业务校验（创建/编辑共用）：alertType 合法（40001）、分类型条件必填（40001）、
     * minQty≥0（Bean 校验）、maxQty≥minQty（同填时，40001）；返回 alertType 码值。
     */
    private Integer validate(AlertRuleSaveDTO dto, Long selfId) {
        Integer alertType = AlertType.toCode(dto.getAlertType()); // 非法 40001
        switch (alertType) {
            case 1 -> { // LOW_STOCK：minQty 或 thresholdValue 二选一必填
                if (dto.getMinQty() == null && dto.getThresholdValue() == null) {
                    throw new BizException(ResultCode.PARAM_INVALID,
                            "LOW_STOCK 规则必须提供 minQty 或 thresholdValue");
                }
            }
            case 2 -> { // HIGH_STOCK：maxQty 或 thresholdValue 二选一必填
                if (dto.getMaxQty() == null && dto.getThresholdValue() == null) {
                    throw new BizException(ResultCode.PARAM_INVALID,
                            "HIGH_STOCK 规则必须提供 maxQty 或 thresholdValue");
                }
            }
            case 3 -> { // NEAR_EXPIRY：nearDays 必填 ≥1（@Min 已校验下限）
                if (dto.getNearDays() == null) {
                    throw new BizException(ResultCode.PARAM_INVALID,
                            "NEAR_EXPIRY 规则必须提供 nearDays（≥1，单位天）");
                }
            }
            case 4 -> { // EXPIRED：无需阈值参数
            }
            default -> throw new BizException(ResultCode.PARAM_INVALID, "alertType 取值非法");
        }
        if (dto.getMinQty() != null && dto.getMaxQty() != null
                && dto.getMaxQty().compareTo(dto.getMinQty()) < 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "maxQty 不能小于 minQty");
        }
        return alertType;
    }

    /** 规则名全局唯一（14.6 待确认采纳「校验唯一」）→ 40904；逻辑删除后可复用。 */
    private void checkRuleNameUnique(String ruleName, Long selfId) {
        Long count = ruleMapper.selectCount(new LambdaQueryWrapper<StockAlertRule>()
                .eq(StockAlertRule::getRuleName, ruleName)
                .ne(selfId != null, StockAlertRule::getId, selfId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "规则名称已存在");
        }
    }

    /** 引用有效性：仓库/分类/商品存在性校验（40401，接口文档 002 规则 2）。 */
    private void checkReferences(AlertRuleSaveDTO dto) {
        if (dto.getWarehouseId() != null && warehouseMapper.selectById(dto.getWarehouseId()) == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "warehouseId 对应仓库不存在");
        }
        if (dto.getCategoryId() != null && categoryMapper.selectById(dto.getCategoryId()) == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "categoryId 对应商品分类不存在");
        }
        if (dto.getProductId() != null && productMapper.selectById(dto.getProductId()) == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "productId 对应商品不存在");
        }
    }

    /** DTO 字段套用到实体（状态缺省 1 启用）。 */
    private void applyDto(StockAlertRule rule, AlertRuleSaveDTO dto, Integer alertType) {
        rule.setRuleName(dto.getRuleName().trim());
        rule.setWarehouseId(dto.getWarehouseId());
        rule.setCategoryId(dto.getCategoryId());
        rule.setProductId(dto.getProductId());
        rule.setAlertType(alertType);
        rule.setThresholdValue(dto.getThresholdValue());
        rule.setNearDays(dto.getNearDays());
        rule.setMinQty(dto.getMinQty());
        rule.setMaxQty(dto.getMaxQty());
        rule.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        rule.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark().trim() : null);
    }

    /** 实体转出参（反查仓库名/分类名/商品名/创建人）。 */
    private AlertRuleVO toVO(StockAlertRule rule) {
        AlertRuleVO vo = new AlertRuleVO();
        vo.setId(rule.getId());
        vo.setRuleName(rule.getRuleName());
        vo.setWarehouseId(rule.getWarehouseId());
        if (rule.getWarehouseId() == null) {
            vo.setWarehouseName("全部仓库");
        } else {
            Warehouse warehouse = warehouseMapper.selectById(rule.getWarehouseId());
            vo.setWarehouseName(warehouse == null ? null : warehouse.getWarehouseName());
        }
        vo.setCategoryId(rule.getCategoryId());
        if (rule.getCategoryId() != null) {
            ProductCategory category = categoryMapper.selectById(rule.getCategoryId());
            vo.setCategoryName(category == null ? null : category.getCategoryName());
        }
        vo.setProductId(rule.getProductId());
        if (rule.getProductId() != null) {
            Product product = productMapper.selectById(rule.getProductId());
            vo.setProductName(product == null ? null : product.getProductName());
        }
        vo.setAlertType(AlertType.toLabel(rule.getAlertType()));
        vo.setAlertTypeDesc(AlertType.toDesc(rule.getAlertType()));
        vo.setThresholdValue(rule.getThresholdValue());
        vo.setNearDays(rule.getNearDays());
        vo.setMinQty(rule.getMinQty());
        vo.setMaxQty(rule.getMaxQty());
        vo.setStatus(rule.getStatus());
        vo.setStatusDesc(rule.getStatus() != null && rule.getStatus() == 1 ? "启用" : "停用");
        vo.setRemark(rule.getRemark());
        vo.setCreateBy(username(rule.getCreateBy()));
        vo.setCreateTime(rule.getCreateTime());
        vo.setUpdateTime(rule.getUpdateTime());
        return vo;
    }

    /** 用户名反查（已删除用户置 null）。 */
    private String username(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getUsername();
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }
}

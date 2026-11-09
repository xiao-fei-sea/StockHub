package com.smartwms.warehouse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.base.mapper.WarehouseMapper;
import com.smartwms.entity.SysUser;
import com.smartwms.entity.Warehouse;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.security.LoginUser;
import com.smartwms.system.mapper.SysUserMapper;
import com.smartwms.vo.PageResult;
import com.smartwms.warehouse.dto.WarehouseCreateDTO;
import com.smartwms.warehouse.dto.WarehousePageQueryDTO;
import com.smartwms.warehouse.dto.WarehouseUpdateDTO;
import com.smartwms.warehouse.service.WarehouseService;
import com.smartwms.warehouse.vo.WarehouseOptionVO;
import com.smartwms.warehouse.vo.WarehouseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 仓库管理服务实现（API-WH-001~006）。
 * 数据权限口径与认证模块一致：LoginUser.warehouseUnrestricted（ROLE_ADMIN、ROLE_VIEWER）
 * 不受限，其余用户仅见 sys_user_warehouse 授权仓库（基线第 5 节）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;

    // ==================== WH-001 分页查询 ====================

    @Override
    public PageResult<WarehouseVO> page(WarehousePageQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : Math.min(dto.getPageSize(), 100);

        Set<Long> allowed = allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            // 受限用户且无任何授权仓库：直接返回空页（不发起查询）
            return PageResult.of(0L, pageNum, pageSize, List.of());
        }

        LambdaQueryWrapper<Warehouse> wrapper = new LambdaQueryWrapper<Warehouse>()
                .eq(dto.getStatus() != null, Warehouse::getStatus, dto.getStatus())
                .in(allowed != null, Warehouse::getId, allowed)
                .orderByAsc(Warehouse::getSort)
                .orderByAsc(Warehouse::getId);
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(Warehouse::getWarehouseCode, like)
                    .or().like(Warehouse::getWarehouseName, like)
                    .or().like(Warehouse::getAddress, like));
        }

        Page<Warehouse> page = warehouseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toVOList(page.getRecords()));
    }

    // ==================== WH-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WarehouseCreateDTO dto) {
        // warehouseCode 全局唯一（并发由 uk_warehouse_code 兜底 → 40904）
        Long exists = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getWarehouseCode, dto.getWarehouseCode().trim()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }

        Warehouse wh = new Warehouse();
        wh.setWarehouseCode(dto.getWarehouseCode().trim());
        wh.setWarehouseName(dto.getWarehouseName().trim());
        wh.setAddress(emptyToNull(dto.getAddress()));
        wh.setContactName(emptyToNull(dto.getContactName()));
        wh.setContactPhone(emptyToNull(dto.getContactPhone()));
        wh.setAreaSize(dto.getAreaSize());
        wh.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        wh.setSort(dto.getSort() == null ? 0 : dto.getSort());
        wh.setRemark(emptyToNull(dto.getRemark()));
        wh.setCreateBy(currentUserId());
        warehouseMapper.insert(wh);
        log.info("新增仓库成功 warehouseId={}, warehouseCode={}, warehouseName={}",
                wh.getId(), wh.getWarehouseCode(), wh.getWarehouseName());
        return wh.getId();
    }

    // ==================== WH-003 详情 ====================

    @Override
    public WarehouseVO detail(Long id) {
        Warehouse wh = requireWarehouse(id);
        checkDataPerm(wh);
        return toVOList(List.of(wh)).get(0);
    }

    // ==================== WH-004 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, WarehouseUpdateDTO dto) {
        Warehouse wh = requireWarehouse(id);
        checkDataPerm(wh);
        // warehouseCode 唯一：排除自身
        Long conflict = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getWarehouseCode, dto.getWarehouseCode().trim())
                .ne(Warehouse::getId, id));
        if (conflict != null && conflict > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }

        wh.setWarehouseCode(dto.getWarehouseCode().trim());
        wh.setWarehouseName(dto.getWarehouseName().trim());
        wh.setAddress(emptyToNull(dto.getAddress()));
        wh.setContactName(emptyToNull(dto.getContactName()));
        wh.setContactPhone(emptyToNull(dto.getContactPhone()));
        wh.setAreaSize(dto.getAreaSize());
        if (dto.getStatus() != null) {
            wh.setStatus(dto.getStatus());
        }
        if (dto.getSort() != null) {
            wh.setSort(dto.getSort());
        }
        wh.setRemark(emptyToNull(dto.getRemark()));
        wh.setUpdateBy(currentUserId());
        warehouseMapper.updateById(wh);
        log.info("修改仓库成功 warehouseId={}, warehouseCode={}", id, wh.getWarehouseCode());
    }

    // ==================== WH-005 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Warehouse wh = requireWarehouse(id);
        checkDataPerm(wh);
        long refs = warehouseMapper.countBusinessRefs(id);
        if (refs > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "存在下级库区、库存或业务单据引用该仓库，不允许删除（请改用停用）");
        }
        warehouseMapper.deleteById(id); // 逻辑删除 deleted=1
        log.info("删除仓库成功 warehouseId={}, warehouseCode={}", id, wh.getWarehouseCode());
    }

    // ==================== WH-006 下拉选项 ====================

    @Override
    public List<WarehouseOptionVO> options(String keyword, Integer status) {
        Set<Long> allowed = allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return List.of();
        }
        // 默认仅返回正常仓库，可显式指定 status
        int st = status == null ? 1 : status;
        LambdaQueryWrapper<Warehouse> wrapper = new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getStatus, st)
                .in(allowed != null, Warehouse::getId, allowed)
                .orderByAsc(Warehouse::getSort)
                .orderByAsc(Warehouse::getId);
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like(Warehouse::getWarehouseCode, like)
                    .or().like(Warehouse::getWarehouseName, like));
        }
        return warehouseMapper.selectList(wrapper).stream()
                .map(w -> WarehouseOptionVO.builder()
                        .id(w.getId())
                        .warehouseCode(w.getWarehouseCode())
                        .warehouseName(w.getWarehouseName())
                        .build())
                .toList();
    }

    // ==================== 私有方法 ====================

    /** 仓库必须存在且未逻辑删除，否则 404。 */
    private Warehouse requireWarehouse(Long id) {
        Warehouse wh = warehouseMapper.selectById(id);
        if (wh == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        return wh;
    }

    /** 数据仓库权限校验：受限用户访问未授权仓库返回 40302。 */
    private void checkDataPerm(Warehouse wh) {
        Set<Long> allowed = allowedWarehouseIds();
        if (allowed == null) {
            return; // 不受限（admin/ROLE_VIEWER）
        }
        if (!allowed.contains(wh.getId())) {
            throw new BizException(ResultCode.NO_WAREHOUSE_PERM, "无数据仓库权限");
        }
    }

    /**
     * 当前用户的授权仓库集合：不受限返回 null（不加过滤）；
     * 受限返回授权 id 集合（可能为空集，表示无任何仓库可见）。
     */
    private Set<Long> allowedWarehouseIds() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return Set.of(); // 理论不可达（全部接口需认证），从严按无权限处理
        }
        if (loginUser.isWarehouseUnrestricted()) {
            return null;
        }
        return loginUser.getWarehouseIds() == null ? Set.of() : Set.copyOf(loginUser.getWarehouseIds());
    }

    /** 仓库列表转 VO：批量反查 createBy/updateBy 用户名（已删除用户置 null）。 */
    private List<WarehouseVO> toVOList(List<Warehouse> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> operatorIds = new HashSet<>();
        list.forEach(w -> {
            if (w.getCreateBy() != null) operatorIds.add(w.getCreateBy());
            if (w.getUpdateBy() != null) operatorIds.add(w.getUpdateBy());
        });
        Map<Long, String> operatorNames = operatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (a, b) -> a));

        List<WarehouseVO> result = new ArrayList<>(list.size());
        for (Warehouse w : list) {
            WarehouseVO vo = new WarehouseVO();
            vo.setId(w.getId());
            vo.setWarehouseCode(w.getWarehouseCode());
            vo.setWarehouseName(w.getWarehouseName());
            vo.setAddress(w.getAddress());
            vo.setContactName(w.getContactName());
            vo.setContactPhone(w.getContactPhone());
            vo.setAreaSize(w.getAreaSize());
            vo.setStatus(w.getStatus());
            vo.setSort(w.getSort());
            vo.setRemark(w.getRemark());
            vo.setCreateTime(w.getCreateTime());
            vo.setUpdateTime(w.getUpdateTime());
            vo.setCreateBy(w.getCreateBy() == null ? null : operatorNames.get(w.getCreateBy()));
            vo.setUpdateBy(w.getUpdateBy() == null ? null : operatorNames.get(w.getUpdateBy()));
            result.add(vo);
        }
        return result;
    }

    /** 当前登录用户 id（未登录返回 null，用于 create_by/update_by）。 */
    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }

    /** 空串归一为 null（可选字段清空）。 */
    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}

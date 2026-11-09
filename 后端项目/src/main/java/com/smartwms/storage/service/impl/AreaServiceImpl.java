package com.smartwms.storage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.base.mapper.StorageAreaMapper;
import com.smartwms.base.mapper.WarehouseMapper;
import com.smartwms.entity.StorageArea;
import com.smartwms.entity.SysUser;
import com.smartwms.entity.Warehouse;
import com.smartwms.enums.AreaType;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.security.WarehouseDataPerm;
import com.smartwms.storage.dto.AreaCreateDTO;
import com.smartwms.storage.dto.AreaPageQueryDTO;
import com.smartwms.storage.dto.AreaUpdateDTO;
import com.smartwms.storage.service.AreaService;
import com.smartwms.storage.vo.AreaOptionVO;
import com.smartwms.storage.vo.AreaVO;
import com.smartwms.system.mapper.SysUserMapper;
import com.smartwms.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 库区管理服务实现（API-AREA-001~005）。
 * 数据仓库权限经 WarehouseDataPerm 复用认证口径（基线第 5 节）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AreaServiceImpl implements AreaService {

    private final StorageAreaMapper areaMapper;
    private final WarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;
    private final WarehouseDataPerm dataPerm;

    // ==================== AREA-001 分页查询 ====================

    @Override
    public PageResult<AreaVO> page(AreaPageQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : Math.min(dto.getPageSize(), 100);

        Integer areaType = AreaType.toCode(dto.getAreaType()); // 非法标签 → 40001
        dataPerm.checkWarehouse(dto.getWarehouseId());         // 越权仓库 → 40302

        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(0L, pageNum, pageSize, List.of());
        }

        LambdaQueryWrapper<StorageArea> wrapper = new LambdaQueryWrapper<StorageArea>()
                .eq(dto.getWarehouseId() != null, StorageArea::getWarehouseId, dto.getWarehouseId())
                .eq(areaType != null, StorageArea::getAreaType, areaType)
                .eq(dto.getStatus() != null, StorageArea::getStatus, dto.getStatus())
                .in(allowed != null, StorageArea::getId, allowed)
                .orderByAsc(StorageArea::getId); // storage_area 表无 sort 字段
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(StorageArea::getAreaCode, like)
                    .or().like(StorageArea::getAreaName, like));
        }

        Page<StorageArea> page = areaMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toVOList(page.getRecords()));
    }

    // ==================== AREA-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AreaCreateDTO dto) {
        dataPerm.checkWarehouse(dto.getWarehouseId()); // 越权 → 40302
        requireWarehouseExists(dto.getWarehouseId());  // 仓库不存在 → 40001
        Integer areaType = AreaType.toCode(dto.getAreaType());
        if (areaType == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "areaType 不能为空");
        }

        // 编码唯一：uk_area_code(warehouse_id, area_code) 并发兜底 → 40904
        if (areaMapper.selectCount(new LambdaQueryWrapper<StorageArea>()
                .eq(StorageArea::getWarehouseId, dto.getWarehouseId())
                .eq(StorageArea::getAreaCode, dto.getAreaCode().trim())) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }
        // 名称同仓库内不重复（按编码唯一优先，名称重复按 40904 处理）
        if (areaMapper.selectCount(new LambdaQueryWrapper<StorageArea>()
                .eq(StorageArea::getWarehouseId, dto.getWarehouseId())
                .eq(StorageArea::getAreaName, dto.getAreaName().trim())) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "同仓库下库区名称已存在");
        }

        StorageArea area = new StorageArea();
        area.setWarehouseId(dto.getWarehouseId());
        area.setAreaCode(dto.getAreaCode().trim());
        area.setAreaName(dto.getAreaName().trim());
        area.setAreaType(areaType);
        area.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        area.setCreateBy(currentUserId());
        areaMapper.insert(area);
        log.info("新增库区成功 areaId={}, areaCode={}, areaName={}",
                area.getId(), area.getAreaCode(), area.getAreaName());
        return area.getId();
    }

    // ==================== AREA-003 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AreaUpdateDTO dto) {
        StorageArea area = requireArea(id);
        dataPerm.checkWarehouse(area.getWarehouseId());
        Integer areaType = AreaType.toCode(dto.getAreaType());
        if (areaType == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "areaType 不能为空");
        }

        // 编码/名称唯一（排除自身）
        if (areaMapper.selectCount(new LambdaQueryWrapper<StorageArea>()
                .eq(StorageArea::getWarehouseId, dto.getWarehouseId())
                .eq(StorageArea::getAreaCode, dto.getAreaCode().trim())
                .ne(StorageArea::getId, id)) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }
        if (areaMapper.selectCount(new LambdaQueryWrapper<StorageArea>()
                .eq(StorageArea::getWarehouseId, dto.getWarehouseId())
                .eq(StorageArea::getAreaName, dto.getAreaName().trim())
                .ne(StorageArea::getId, id)) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "同仓库下库区名称已存在");
        }

        // 变更所属仓库：库区下货位存在库存时禁止（40901）；目标仓库同样受数据权限约束
        if (!dto.getWarehouseId().equals(area.getWarehouseId())) {
            if (areaMapper.countInventory(id) > 0) {
                throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                        "库区下货位存在库存，禁止变更所属仓库");
            }
            dataPerm.checkWarehouse(dto.getWarehouseId());
        }

        area.setWarehouseId(dto.getWarehouseId());
        area.setAreaCode(dto.getAreaCode().trim());
        area.setAreaName(dto.getAreaName().trim());
        area.setAreaType(areaType);
        if (dto.getStatus() != null) {
            area.setStatus(dto.getStatus());
        }
        area.setUpdateBy(currentUserId());
        areaMapper.updateById(area);
        log.info("修改库区成功 areaId={}, areaCode={}", id, area.getAreaCode());
    }

    // ==================== AREA-004 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        StorageArea area = requireArea(id);
        dataPerm.checkWarehouse(area.getWarehouseId());
        if (areaMapper.countRacks(id) > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "存在下级货架，禁止删除（请自底向上先删货架）");
        }
        if (areaMapper.countInventory(id) > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "库区被库存引用，不允许删除（请改用停用）");
        }
        areaMapper.deleteById(id); // 逻辑删除 deleted=1
        log.info("删除库区成功 areaId={}, areaCode={}", id, area.getAreaCode());
    }

    // ==================== AREA-005 下拉选项 ====================

    @Override
    public List<AreaOptionVO> options(Long warehouseId, String keyword, Integer status) {
        if (warehouseId == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "warehouseId 不能为空");
        }
        dataPerm.checkWarehouse(warehouseId); // 越权 → 40302

        int st = status == null ? 1 : status;
        LambdaQueryWrapper<StorageArea> wrapper = new LambdaQueryWrapper<StorageArea>()
                .eq(StorageArea::getWarehouseId, warehouseId)
                .eq(StorageArea::getStatus, st)
                .orderByAsc(StorageArea::getId);
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like(StorageArea::getAreaCode, like)
                    .or().like(StorageArea::getAreaName, like));
        }
        return areaMapper.selectList(wrapper).stream()
                .map(a -> AreaOptionVO.builder()
                        .id(a.getId())
                        .areaCode(a.getAreaCode())
                        .areaName(a.getAreaName())
                        .areaType(AreaType.toLabel(a.getAreaType()))
                        .build())
                .toList();
    }

    // ==================== 私有方法 ====================

    /** 库区必须存在且未逻辑删除，否则 404。 */
    private StorageArea requireArea(Long id) {
        StorageArea area = areaMapper.selectById(id);
        if (area == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        return area;
    }

    /** 仓库必须存在且未删除，否则 40001（作为入参引用非法）。 */
    private void requireWarehouseExists(Long warehouseId) {
        if (warehouseMapper.selectById(warehouseId) == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "warehouseId 对应仓库不存在");
        }
    }

    /** 库区列表转 VO：批量反查仓库名与 createBy/updateBy 用户名（查不到置 null）。 */
    private List<AreaVO> toVOList(List<StorageArea> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> warehouseIds = new HashSet<>();
        Set<Long> operatorIds = new HashSet<>();
        list.forEach(a -> {
            warehouseIds.add(a.getWarehouseId());
            if (a.getCreateBy() != null) operatorIds.add(a.getCreateBy());
            if (a.getUpdateBy() != null) operatorIds.add(a.getUpdateBy());
        });
        Map<Long, String> warehouseNames = warehouseMapper.selectBatchIds(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getWarehouseName, (x, y) -> x));
        Map<Long, String> operatorNames = operatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (x, y) -> x));

        List<AreaVO> result = new ArrayList<>(list.size());
        for (StorageArea a : list) {
            AreaVO vo = new AreaVO();
            vo.setId(a.getId());
            vo.setWarehouseId(a.getWarehouseId());
            vo.setWarehouseName(warehouseNames.get(a.getWarehouseId()));
            vo.setAreaCode(a.getAreaCode());
            vo.setAreaName(a.getAreaName());
            vo.setAreaType(AreaType.toLabel(a.getAreaType()));
            vo.setStatus(a.getStatus());
            vo.setCreateTime(a.getCreateTime());
            vo.setUpdateTime(a.getUpdateTime());
            vo.setCreateBy(a.getCreateBy() == null ? null : operatorNames.get(a.getCreateBy()));
            vo.setUpdateBy(a.getUpdateBy() == null ? null : operatorNames.get(a.getUpdateBy()));
            result.add(vo);
        }
        return result;
    }

    /** 当前登录用户 id（操作人）。 */
    private Long currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.smartwms.security.LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }
}

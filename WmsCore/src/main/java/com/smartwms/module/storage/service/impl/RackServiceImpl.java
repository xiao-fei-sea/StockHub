package com.smartwms.module.storage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.module.storage.mapper.LocationMapper;
import com.smartwms.module.storage.mapper.RackMapper;
import com.smartwms.module.storage.mapper.StorageAreaMapper;
import com.smartwms.entity.Location;
import com.smartwms.entity.Rack;
import com.smartwms.entity.StorageArea;
import com.smartwms.entity.SysUser;
import com.smartwms.common.enums.ResultCode;
import com.smartwms.common.BizException;
import com.smartwms.framework.security.LoginUser;
import com.smartwms.framework.security.WarehouseDataPerm;
import com.smartwms.module.storage.dto.RackCreateDTO;
import com.smartwms.module.storage.dto.RackPageQueryDTO;
import com.smartwms.module.storage.dto.RackUpdateDTO;
import com.smartwms.module.storage.service.RackService;
import com.smartwms.module.storage.vo.RackVO;
import com.smartwms.module.system.mapper.SysUserMapper;
import com.smartwms.common.PageResult;
import com.smartwms.common.PageUtils;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 货架管理服务实现（API-RACK-001~004）。
 * rack 表无 sort 字段，列表按 id 升序排列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RackServiceImpl implements RackService {

    /** 货位编码尾部「层2位+列2位」（批量生成编码规则，基线第 10 节），用于推算已覆盖层数/列数。 */
    private static final Pattern FLOOR_COLUMN_SUFFIX = Pattern.compile("(\\d{2})(\\d{2})$");

    private final RackMapper rackMapper;
    private final StorageAreaMapper areaMapper;
    private final LocationMapper locationMapper;
    private final SysUserMapper userMapper;
    private final WarehouseDataPerm dataPerm;

    // ==================== RACK-001 分页查询 ====================

    @Override
    public PageResult<RackVO> page(RackPageQueryDTO dto) {

        // warehouseId/areaId 组合须匹配（areaId 属于 warehouseId），否则 40001
        if (dto.getWarehouseId() != null && dto.getAreaId() != null) {
            StorageArea area = areaMapper.selectById(dto.getAreaId());
            if (area == null || !dto.getWarehouseId().equals(area.getWarehouseId())) {
                throw new BizException(ResultCode.PARAM_INVALID, "areaId 与 warehouseId 不匹配");
            }
        }
        dataPerm.checkWarehouse(dto.getWarehouseId());

        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(0L, dto.pageNum(), dto.pageSize(), List.of());
        }

        LambdaQueryWrapper<Rack> wrapper = new LambdaQueryWrapper<Rack>()
                .eq(dto.getWarehouseId() != null, Rack::getWarehouseId, dto.getWarehouseId())
                .eq(dto.getAreaId() != null, Rack::getAreaId, dto.getAreaId())
                .eq(dto.getStatus() != null, Rack::getStatus, dto.getStatus())
                .in(allowed != null, Rack::getWarehouseId, allowed)
                .orderByAsc(Rack::getId);
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(Rack::getRackCode, like)
                    .or().like(Rack::getRackName, like));
        }

        return PageUtils.query(rackMapper, dto, wrapper, this::toVOList);
    }

    // ==================== RACK-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RackCreateDTO dto) {
        dataPerm.checkWarehouse(dto.getWarehouseId()); // 越权 → 40302
        StorageArea area = requireAreaForCreate(dto.getAreaId(), dto.getWarehouseId());

        // rackCode 唯一：uk_rack_code(area_id, rack_code) 并发兜底 → 40904
        if (rackMapper.selectCount(new LambdaQueryWrapper<Rack>()
                .eq(Rack::getAreaId, area.getId())
                .eq(Rack::getRackCode, dto.getRackCode().trim())) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }

        Rack rack = new Rack();
        rack.setWarehouseId(dto.getWarehouseId());
        rack.setAreaId(area.getId());
        rack.setRackCode(dto.getRackCode().trim());
        rack.setRackName(dto.getRackName().trim());
        rack.setFloorCount(dto.getFloorCount() == null ? 1 : dto.getFloorCount());
        rack.setColumnCount(dto.getColumnCount() == null ? 1 : dto.getColumnCount());
        rack.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        rack.setCreateBy(currentUserId());
        rackMapper.insert(rack);
        log.info("新增货架成功 rackId={}, rackCode={}, areaId={}",
                rack.getId(), rack.getRackCode(), rack.getAreaId());
        return rack.getId();
    }

    // ==================== RACK-003 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RackUpdateDTO dto) {
        Rack rack = requireRack(id);
        dataPerm.checkWarehouse(rack.getWarehouseId());

        // areaId 校验同新增：存在未删除且属于 dto.warehouseId，否则 40001
        StorageArea area = requireAreaForCreate(dto.getAreaId(), dto.getWarehouseId());

        boolean hierarchyChanged = !area.getId().equals(rack.getAreaId())
                || !dto.getWarehouseId().equals(rack.getWarehouseId());

        List<Location> existing = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                .eq(Location::getRackId, id));

        if (!existing.isEmpty()) {
            if (hierarchyChanged) {
                throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                        "货架下已有货位，禁止变更所属库区/仓库");
            }
            // 层数/列数不得小于已生成货位覆盖的层数/列数（40901）
            int coveredFloor = coveredValue(existing, 0);
            int coveredColumn = coveredValue(existing, 1);
            if (dto.getFloorCount() != null && dto.getFloorCount() < coveredFloor) {
                throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                        "层数不得小于已生成货位覆盖的层数（" + coveredFloor + "）");
            }
            if (dto.getColumnCount() != null && dto.getColumnCount() < coveredColumn) {
                throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                        "列数不得小于已生成货位覆盖的列数（" + coveredColumn + "）");
            }
        }

        // rackCode 同目标库区唯一（排除自身）
        if (rackMapper.selectCount(new LambdaQueryWrapper<Rack>()
                .eq(Rack::getAreaId, area.getId())
                .eq(Rack::getRackCode, dto.getRackCode().trim())
                .ne(Rack::getId, id)) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }

        rack.setWarehouseId(dto.getWarehouseId());
        rack.setAreaId(area.getId());
        rack.setRackCode(dto.getRackCode().trim());
        rack.setRackName(dto.getRackName().trim());
        if (dto.getFloorCount() != null) {
            rack.setFloorCount(dto.getFloorCount());
        }
        if (dto.getColumnCount() != null) {
            rack.setColumnCount(dto.getColumnCount());
        }
        if (dto.getStatus() != null) {
            rack.setStatus(dto.getStatus());
        }
        rack.setUpdateBy(currentUserId());
        rackMapper.updateById(rack);
        log.info("修改货架成功 rackId={}, rackCode={}", id, rack.getRackCode());
    }

    // ==================== RACK-004 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Rack rack = requireRack(id);
        dataPerm.checkWarehouse(rack.getWarehouseId());
        if (rackMapper.countLocations(id) > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "存在下级货位，禁止删除（请先删货位）");
        }
        if (rackMapper.countInventory(id) > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "货位被库存引用（间接引用），不允许删除（请改用停用）");
        }
        rackMapper.deleteById(id); // 逻辑删除 deleted=1
        log.info("删除货架成功 rackId={}, rackCode={}", id, rack.getRackCode());
    }

    // ==================== 私有方法 ====================

    /** 货架必须存在且未逻辑删除，否则 404。 */
    private Rack requireRack(Long id) {
        Rack rack = rackMapper.selectById(id);
        if (rack == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        return rack;
    }

    /** 新增/修改路径的库区校验：存在未删除且属于指定仓库，否则 40001。 */
    private StorageArea requireAreaForCreate(Long areaId, Long warehouseId) {
        StorageArea area = areaMapper.selectById(areaId);
        if (area == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "areaId 对应库区不存在");
        }
        if (!warehouseId.equals(area.getWarehouseId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "areaId 与 warehouseId 不匹配");
        }
        return area;
    }

    /** 已覆盖层数（index=0）/列数（index=1）：从货位编码尾部 2+2 位推算，无法解析的编码忽略。 */
    private int coveredValue(List<Location> locations, int index) {
        int max = 0;
        for (Location loc : locations) {
            Matcher m = FLOOR_COLUMN_SUFFIX.matcher(loc.getLocationCode() == null ? "" : loc.getLocationCode());
            if (m.find()) {
                max = Math.max(max, Integer.parseInt(m.group(index + 1)));
            }
        }
        return max;
    }

    /** 货架列表转 VO：批量反查库区名与操作人用户名（查不到置 null）。 */
    private List<RackVO> toVOList(List<Rack> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> areaIds = new HashSet<>();
        Set<Long> operatorIds = new HashSet<>();
        list.forEach(r -> {
            areaIds.add(r.getAreaId());
            if (r.getCreateBy() != null) operatorIds.add(r.getCreateBy());
            if (r.getUpdateBy() != null) operatorIds.add(r.getUpdateBy());
        });
        Map<Long, String> areaNames = areaMapper.selectBatchIds(areaIds).stream()
                .collect(Collectors.toMap(StorageArea::getId, StorageArea::getAreaName, (x, y) -> x));
        Map<Long, String> operatorNames = operatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (x, y) -> x));

        List<RackVO> result = new ArrayList<>(list.size());
        for (Rack r : list) {
            RackVO vo = new RackVO();
            vo.setId(r.getId());
            vo.setWarehouseId(r.getWarehouseId());
            vo.setAreaId(r.getAreaId());
            vo.setAreaName(areaNames.get(r.getAreaId()));
            vo.setRackCode(r.getRackCode());
            vo.setRackName(r.getRackName());
            vo.setFloorCount(r.getFloorCount());
            vo.setColumnCount(r.getColumnCount());
            vo.setStatus(r.getStatus());
            vo.setCreateTime(r.getCreateTime());
            vo.setUpdateTime(r.getUpdateTime());
            vo.setCreateBy(r.getCreateBy() == null ? null : operatorNames.get(r.getCreateBy()));
            vo.setUpdateBy(r.getUpdateBy() == null ? null : operatorNames.get(r.getUpdateBy()));
            result.add(vo);
        }
        return result;
    }

    /** 当前登录用户 id（操作人）。 */
    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }
}

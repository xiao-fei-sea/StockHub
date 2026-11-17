package com.smartwms.module.storage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.module.storage.mapper.LocationMapper;
import com.smartwms.module.storage.mapper.RackMapper;
import com.smartwms.module.storage.mapper.StorageAreaMapper;
import com.smartwms.entity.Location;
import com.smartwms.entity.Rack;
import com.smartwms.entity.StorageArea;
import com.smartwms.entity.SysUser;
import com.smartwms.common.enums.LocationState;
import com.smartwms.common.enums.LocationType;
import com.smartwms.common.enums.ResultCode;
import com.smartwms.common.BizException;
import com.smartwms.framework.security.LoginUser;
import com.smartwms.framework.security.WarehouseDataPerm;
import com.smartwms.module.storage.dto.LocationBatchGenerateDTO;
import com.smartwms.module.storage.dto.LocationCreateDTO;
import com.smartwms.module.storage.dto.LocationPageQueryDTO;
import com.smartwms.module.storage.dto.LocationUpdateDTO;
import com.smartwms.module.storage.service.LocationService;
import com.smartwms.module.storage.vo.LocationBatchResultVO;
import com.smartwms.module.storage.vo.LocationOptionVO;
import com.smartwms.module.storage.vo.LocationVO;
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
import java.util.stream.Collectors;

/**
 * 货位管理服务实现（API-LOC-001~006）。
 * 类型/状态在接口层用中文标签，服务层经 LocationType/LocationState 转 TINYINT 存取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    /** 单次批量生成上限（LOC-006 规则2，防误操作生成超量货位）。 */
    private static final int BATCH_LIMIT = 500;

    /** options 最多返回条数（LOC-005 规则2，约 3000 货位须限制全量拉取）。 */
    private static final int OPTIONS_LIMIT = 500;

    private static final Integer STATE_OCCUPIED = LocationState.OCCUPIED.getCode();
    private static final Integer STATE_DISABLED = LocationState.DISABLED.getCode();

    private final LocationMapper locationMapper;
    private final RackMapper rackMapper;
    private final StorageAreaMapper areaMapper;
    private final SysUserMapper userMapper;
    private final WarehouseDataPerm dataPerm;

    // ==================== LOC-001 分页查询 ====================

    @Override
    public PageResult<LocationVO> page(LocationPageQueryDTO dto) {

        checkHierarchy(dto.getWarehouseId(), dto.getAreaId(), dto.getRackId()); // 40001
        Integer stateCode = LocationState.toCode(dto.getCurrentStatus());       // 40001
        Integer typeCode = LocationType.toCode(dto.getLocationType());          // 40001
        dataPerm.checkWarehouse(dto.getWarehouseId());                          // 40302

        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(0L, dto.pageNum(), dto.pageSize(), List.of());
        }

        LambdaQueryWrapper<Location> wrapper = new LambdaQueryWrapper<Location>()
                .eq(dto.getWarehouseId() != null, Location::getWarehouseId, dto.getWarehouseId())
                .eq(dto.getAreaId() != null, Location::getAreaId, dto.getAreaId())
                .eq(dto.getRackId() != null, Location::getRackId, dto.getRackId())
                .eq(dto.getStatus() != null, Location::getStatus, dto.getStatus())
                .eq(stateCode != null, Location::getCurrentStatus, stateCode)
                .eq(typeCode != null, Location::getLocationType, typeCode)
                .in(allowed != null, Location::getWarehouseId, allowed)
                .orderByAsc(Location::getSort)
                .orderByAsc(Location::getId);
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(Location::getLocationCode, like)
                    .or().like(Location::getLocationName, like));
        }

        return PageUtils.query(locationMapper, dto, wrapper, this::toVOList);
    }

    // ==================== LOC-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(LocationCreateDTO dto) {
        checkHierarchy(dto.getWarehouseId(), dto.getAreaId(), dto.getRackId()); // 40001
        dataPerm.checkWarehouse(dto.getWarehouseId());                          // 40302

        Integer typeCode = LocationType.toCode(dto.getLocationType());
        if (typeCode == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "locationType 不能为空");
        }
        Integer stateCode = LocationState.toCode(dto.getCurrentStatus());
        if (stateCode == null) {
            stateCode = LocationState.FREE.getCode(); // 缺省「空闲」
        }
        if (STATE_OCCUPIED.equals(stateCode)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "「占用」由库存联动维护，人工不可直接设置");
        }

        // locationCode 全局唯一：uk_location_code 并发兜底 → 40904
        if (locationMapper.selectCount(new LambdaQueryWrapper<Location>()
                .eq(Location::getLocationCode, dto.getLocationCode().trim())) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }

        Location loc = new Location();
        loc.setWarehouseId(dto.getWarehouseId());
        loc.setAreaId(dto.getAreaId());
        loc.setRackId(dto.getRackId());
        loc.setLocationCode(dto.getLocationCode().trim());
        loc.setLocationName(dto.getLocationName().trim());
        loc.setLocationType(typeCode);
        loc.setCapacityVolume(dto.getCapacityVolume());
        loc.setCurrentStatus(stateCode);
        loc.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        loc.setSort(dto.getSort() == null ? 0 : dto.getSort());
        loc.setCreateBy(currentUserId());
        locationMapper.insert(loc);
        log.info("新增货位成功 locationId={}, locationCode={}", loc.getId(), loc.getLocationCode());
        return loc.getId();
    }

    // ==================== LOC-003 修改 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, LocationUpdateDTO dto) {
        Location loc = locationMapper.selectById(id);
        if (loc == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        dataPerm.checkWarehouse(loc.getWarehouseId());

        checkHierarchy(dto.getWarehouseId(), dto.getAreaId(), dto.getRackId()); // 40001
        Integer typeCode = LocationType.toCode(dto.getLocationType());
        Integer stateCode = LocationState.toCode(dto.getCurrentStatus()); // null 表示不变

        // locationCode 全局唯一（排除自身）
        if (locationMapper.selectCount(new LambdaQueryWrapper<Location>()
                .eq(Location::getLocationCode, dto.getLocationCode().trim())
                .ne(Location::getId, id)) > 0) {
            throw new BizException(ResultCode.CODE_EXISTS, "编码已存在");
        }

        boolean hierarchyChanged = !dto.getWarehouseId().equals(loc.getWarehouseId())
                || !dto.getAreaId().equals(loc.getAreaId())
                || !dto.getRackId().equals(loc.getRackId());
        long inventoryCount = locationMapper.countInventory(id);

        // 规则2：有库存的货位禁止变更层级（40901）
        if (hierarchyChanged && inventoryCount > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "货位存在库存，禁止变更所属仓库/库区/货架");
        }
        // 规则3：「占用」由库存写入联动，人工不可直接设置（40901）
        if (STATE_OCCUPIED.equals(stateCode) && !STATE_OCCUPIED.equals(loc.getCurrentStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "「占用」由库存联动维护，人工不可直接设置");
        }
        // 规则2/4：有库存时禁止置为禁用、禁止停用（40906，先清空库存）
        if (inventoryCount > 0
                && (STATE_DISABLED.equals(stateCode) || Integer.valueOf(0).equals(dto.getStatus()))) {
            throw new BizException(ResultCode.LOCATION_UNAVAILABLE,
                    "货位存在库存，禁止置为禁用/停用（请先清空库存）");
        }

        loc.setWarehouseId(dto.getWarehouseId());
        loc.setAreaId(dto.getAreaId());
        loc.setRackId(dto.getRackId());
        loc.setLocationCode(dto.getLocationCode().trim());
        loc.setLocationName(dto.getLocationName().trim());
        loc.setLocationType(typeCode);
        if (dto.getCapacityVolume() != null) {
            loc.setCapacityVolume(dto.getCapacityVolume());
        }
        if (stateCode != null) {
            loc.setCurrentStatus(stateCode);
        }
        if (dto.getStatus() != null) {
            loc.setStatus(dto.getStatus());
        }
        if (dto.getSort() != null) {
            loc.setSort(dto.getSort());
        }
        loc.setUpdateBy(currentUserId());
        locationMapper.updateById(loc);
        log.info("修改货位成功 locationId={}, locationCode={}", id, loc.getLocationCode());
    }

    // ==================== LOC-004 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Location loc = locationMapper.selectById(id);
        if (loc == null) {
            throw new BizException(ResultCode.NOT_FOUND, "资源不存在");
        }
        dataPerm.checkWarehouse(loc.getWarehouseId());
        if (locationMapper.countInventory(id) > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "货位存在库存记录，禁止删除（请先移库清空）");
        }
        if (locationMapper.countOrderItemRefs(id) > 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "货位被单据明细引用，禁止删除");
        }
        locationMapper.deleteById(id); // 逻辑删除 deleted=1
        log.info("删除货位成功 locationId={}, locationCode={}", id, loc.getLocationCode());
    }

    // ==================== LOC-005 下拉选项 ====================

    @Override
    public List<LocationOptionVO> options(Long warehouseId, Long areaId, Long rackId,
                                          String locationType, String currentStatus, String keyword) {
        checkHierarchy(warehouseId, areaId, rackId); // 40001
        dataPerm.checkWarehouse(warehouseId);        // 40302
        Integer typeCode = LocationType.toCode(locationType);
        // 默认仅返回「空闲」可用货位，可显式指定
        Integer stateCode = currentStatus == null
                ? LocationState.FREE.getCode() : LocationState.toCode(currentStatus);

        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<Location> wrapper = new LambdaQueryWrapper<Location>()
                .eq(warehouseId != null, Location::getWarehouseId, warehouseId)
                .eq(areaId != null, Location::getAreaId, areaId)
                .eq(rackId != null, Location::getRackId, rackId)
                .eq(Location::getStatus, 1) // 默认过滤停用货位（可用性口径）
                .eq(stateCode != null, Location::getCurrentStatus, stateCode)
                .eq(typeCode != null, Location::getLocationType, typeCode)
                .in(allowed != null, Location::getWarehouseId, allowed)
                .orderByAsc(Location::getSort)
                .orderByAsc(Location::getId)
                .last("LIMIT " + OPTIONS_LIMIT);
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            wrapper.and(w -> w.like(Location::getLocationCode, like)
                    .or().like(Location::getLocationName, like));
        }

        return locationMapper.selectList(wrapper).stream()
                .map(l -> LocationOptionVO.builder()
                        .id(l.getId())
                        .locationCode(l.getLocationCode())
                        .locationName(l.getLocationName())
                        .locationType(LocationType.toLabel(l.getLocationType()))
                        .currentStatus(LocationState.toLabel(l.getCurrentStatus()))
                        .build())
                .toList();
    }

    // ==================== LOC-006 批量生成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LocationBatchResultVO batchGenerate(LocationBatchGenerateDTO dto) {
        Rack rack = checkHierarchyReturnRack(dto.getWarehouseId(), dto.getAreaId(), dto.getRackId());
        dataPerm.checkWarehouse(dto.getWarehouseId()); // 40302

        int floorCount = dto.getFloorCount() == null ? rack.getFloorCount() : dto.getFloorCount();
        int columnCount = dto.getColumnCount() == null ? rack.getColumnCount() : dto.getColumnCount();
        if (floorCount < 1 || columnCount < 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "生成层数/列数必须为正整数");
        }
        long total = (long) floorCount * columnCount;
        if (total > BATCH_LIMIT) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "单次批量生成上限为 " + BATCH_LIMIT + " 个货位");
        }

        Integer typeCode = LocationType.toCode(dto.getLocationType());
        if (typeCode == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "locationType 不能为空");
        }

        String prefix = StringUtils.hasText(dto.getPrefix()) ? dto.getPrefix().trim()
                : defaultPrefix(rack);
        int startFloor = dto.getStartFloor() == null ? 1 : dto.getStartFloor();
        int startColumn = dto.getStartColumn() == null ? 1 : dto.getStartColumn();

        // 生成编码与名称：编码 = prefix + 层2位 + 列2位（全局唯一）
        List<String> codes = new ArrayList<>((int) total);
        List<String> names = new ArrayList<>((int) total);
        List<Integer> floors = new ArrayList<>((int) total);
        List<Integer> columns = new ArrayList<>((int) total);
        for (int f = 0; f < floorCount; f++) {
            for (int c = 0; c < columnCount; c++) {
                int floor = startFloor + f;
                int column = startColumn + c;
                codes.add(prefix + String.format("%02d", floor) + String.format("%02d", column));
                names.add(floor + "-" + column);
                floors.add(floor);
                columns.add(column);
            }
        }

        Set<String> existing = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                        .in(Location::getLocationCode, codes)).stream()
                .map(Location::getLocationCode)
                .collect(Collectors.toSet());

        boolean skipExists = dto.getSkipExists() == null || dto.getSkipExists();
        if (!skipExists && !existing.isEmpty()) {
            throw new BizException(ResultCode.CODE_EXISTS,
                    "编码已存在（skipExists=false）：" + existing.iterator().next());
        }

        Long operator = currentUserId();
        int created = 0;
        int skipped = 0;
        String firstCreated = null;
        String lastCreated = null;
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            if (existing.contains(code)) {
                skipped++;
                continue;
            }
            Location loc = new Location();
            loc.setWarehouseId(dto.getWarehouseId());
            loc.setAreaId(dto.getAreaId());
            loc.setRackId(dto.getRackId());
            loc.setLocationCode(code);
            loc.setLocationName(names.get(i));
            loc.setLocationType(typeCode);
            loc.setCapacityVolume(dto.getCapacityVolume());
            loc.setCurrentStatus(LocationState.FREE.getCode()); // 规则5：初始「空闲」
            loc.setStatus(1);
            loc.setSort(0);
            loc.setCreateBy(operator);
            locationMapper.insert(loc);
            created++;
            if (firstCreated == null) {
                firstCreated = code;
            }
            lastCreated = code;
        }

        log.info("批量生成货位完成 rackId={}, total={}, created={}, skipped={}",
                rack.getId(), total, created, skipped);
        return LocationBatchResultVO.builder()
                .totalCount((int) total)
                .createdCount(created)
                .skippedCount(skipped)
                .firstLocationCode(firstCreated)
                .lastLocationCode(lastCreated)
                .build();
    }

    // ==================== 私有方法 ====================

    /**
     * 层级自洽校验（LOC-001 规则1）：rackId 属于 areaId、areaId 属于 warehouseId，
     * 关联记录不存在或不匹配一律返回 40001。
     */
    private void checkHierarchy(Long warehouseId, Long areaId, Long rackId) {
        checkHierarchyReturnRack(warehouseId, areaId, rackId);
    }

    /** 同上，返回 rack（批量生成需要货架默认层数/列数；未传 rackId 返回 null）。 */
    private Rack checkHierarchyReturnRack(Long warehouseId, Long areaId, Long rackId) {
        Rack rack = null;
        if (rackId != null) {
            rack = rackMapper.selectById(rackId);
            if (rack == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "rackId 对应货架不存在");
            }
            if (areaId != null && !areaId.equals(rack.getAreaId())) {
                throw new BizException(ResultCode.PARAM_INVALID, "rackId 与 areaId 不匹配");
            }
            if (warehouseId != null && !warehouseId.equals(rack.getWarehouseId())) {
                throw new BizException(ResultCode.PARAM_INVALID, "rackId 与 warehouseId 不匹配");
            }
        }
        if (areaId != null) {
            StorageArea area = areaMapper.selectById(areaId);
            if (area == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "areaId 对应库区不存在");
            }
            if (warehouseId != null && !warehouseId.equals(area.getWarehouseId())) {
                throw new BizException(ResultCode.PARAM_INVALID, "areaId 与 warehouseId 不匹配");
            }
        }
        return rack;
    }

    /** LOC-006 编码前缀缺省推导：货架编码 JH-xxx → HW-0xxx（无数字时用货架 id），保证生成码 ≤50 字符。 */
    private String defaultPrefix(Rack rack) {
        String digits = rack.getRackCode() == null ? "" : rack.getRackCode().replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            digits = String.valueOf(rack.getId());
        }
        long value = Long.parseLong(digits.length() > 4 ? digits.substring(digits.length() - 4) : digits);
        return String.format("HW-%04d", value);
    }

    /** 货位列表转 VO：批量反查库区名、货架名与操作人用户名（查不到置 null）。 */
    private List<LocationVO> toVOList(List<Location> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> areaIds = new HashSet<>();
        Set<Long> rackIds = new HashSet<>();
        Set<Long> operatorIds = new HashSet<>();
        list.forEach(l -> {
            areaIds.add(l.getAreaId());
            rackIds.add(l.getRackId());
            if (l.getCreateBy() != null) operatorIds.add(l.getCreateBy());
            if (l.getUpdateBy() != null) operatorIds.add(l.getUpdateBy());
        });
        Map<Long, String> areaNames = areaMapper.selectBatchIds(areaIds).stream()
                .collect(Collectors.toMap(StorageArea::getId, StorageArea::getAreaName, (x, y) -> x));
        Map<Long, String> rackNames = rackMapper.selectBatchIds(rackIds).stream()
                .collect(Collectors.toMap(Rack::getId, Rack::getRackName, (x, y) -> x));
        Map<Long, String> operatorNames = operatorIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (x, y) -> x));

        List<LocationVO> result = new ArrayList<>(list.size());
        for (Location l : list) {
            LocationVO vo = new LocationVO();
            vo.setId(l.getId());
            vo.setWarehouseId(l.getWarehouseId());
            vo.setAreaId(l.getAreaId());
            vo.setRackId(l.getRackId());
            vo.setAreaName(areaNames.get(l.getAreaId()));
            vo.setRackName(rackNames.get(l.getRackId()));
            vo.setLocationCode(l.getLocationCode());
            vo.setLocationName(l.getLocationName());
            vo.setLocationType(LocationType.toLabel(l.getLocationType()));
            vo.setCapacityVolume(l.getCapacityVolume());
            vo.setCurrentStatus(LocationState.toLabel(l.getCurrentStatus()));
            vo.setStatus(l.getStatus());
            vo.setSort(l.getSort());
            vo.setCreateTime(l.getCreateTime());
            vo.setUpdateTime(l.getUpdateTime());
            vo.setCreateBy(l.getCreateBy() == null ? null : operatorNames.get(l.getCreateBy()));
            vo.setUpdateBy(l.getUpdateBy() == null ? null : operatorNames.get(l.getUpdateBy()));
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

package com.smartwms.module.storage.controller;

import com.smartwms.framework.aspect.OperationLog;
import com.smartwms.module.storage.dto.LocationBatchGenerateDTO;
import com.smartwms.module.storage.dto.LocationCreateDTO;
import com.smartwms.module.storage.dto.LocationPageQueryDTO;
import com.smartwms.module.storage.dto.LocationUpdateDTO;
import com.smartwms.module.storage.service.LocationService;
import com.smartwms.module.storage.vo.LocationBatchResultVO;
import com.smartwms.module.storage.vo.LocationOptionVO;
import com.smartwms.module.storage.vo.LocationVO;
import com.smartwms.common.PageResult;
import com.smartwms.common.Result;
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

import java.util.List;

/**
 * 货位管理接口 API-LOC-001~006（详见《库位管理接口文档》）。
 * 全部需 JWT 认证；查询类无按钮权限（结果按数据仓库权限过滤），
 * 写操作按 sys:location:* 校验；批量生成复用 sys:location:add（是否单列权限待确认）。
 */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /** LOC-001 货位分页查询（层级自洽 40001，按 sort/id 升序）。 */
    @GetMapping
    public Result<PageResult<LocationVO>> page(@Valid LocationPageQueryDTO dto) {
        return Result.success(locationService.page(dto));
    }

    /** LOC-005 货位下拉选项（默认仅正常+空闲，最多 500 条，字面量路径优先于 /{id}）。 */
    @GetMapping("/options")
    public Result<List<LocationOptionVO>> options(@RequestParam(required = false) Long warehouseId,
                                                  @RequestParam(required = false) Long areaId,
                                                  @RequestParam(required = false) Long rackId,
                                                  @RequestParam(required = false) String locationType,
                                                  @RequestParam(required = false) String currentStatus,
                                                  @RequestParam(required = false) String keyword) {
        return Result.success(locationService.options(warehouseId, areaId, rackId,
                locationType, currentStatus, keyword));
    }

    /** LOC-002 新增货位（locationCode 全局唯一 40904）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:location:add')")
    @OperationLog(module = "货位管理", title = "新增货位")
    public Result<Long> create(@RequestBody @Valid LocationCreateDTO dto) {
        return Result.success(locationService.create(dto));
    }

    /** LOC-006 批量生成货位（按货架层数×列数，单次 ≤500，单事务整体回滚）。 */
    @PostMapping("/batch")
    @PreAuthorize("@perm.has('sys:location:add')")
    @OperationLog(module = "货位管理", title = "批量生成货位")
    public Result<LocationBatchResultVO> batch(@RequestBody @Valid LocationBatchGenerateDTO dto) {
        return Result.success(locationService.batchGenerate(dto));
    }

    /** LOC-003 修改货位（有库存禁改层级 40901、禁置禁用/停用 40906）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:location:edit')")
    @OperationLog(module = "货位管理", title = "修改货位")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid LocationUpdateDTO dto) {
        locationService.update(id, dto);
        return Result.success();
    }

    /** LOC-004 删除货位（存在库存/单据明细引用时 40901，逻辑删除）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:location:delete')")
    @OperationLog(module = "货位管理", title = "删除货位")
    public Result<Void> delete(@PathVariable Long id) {
        locationService.delete(id);
        return Result.success();
    }
}

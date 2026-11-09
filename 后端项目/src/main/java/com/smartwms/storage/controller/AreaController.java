package com.smartwms.storage.controller;

import com.smartwms.aspect.OperationLog;
import com.smartwms.storage.dto.AreaCreateDTO;
import com.smartwms.storage.dto.AreaPageQueryDTO;
import com.smartwms.storage.dto.AreaUpdateDTO;
import com.smartwms.storage.service.AreaService;
import com.smartwms.storage.vo.AreaOptionVO;
import com.smartwms.storage.vo.AreaVO;
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

import java.util.List;

/**
 * 库区管理接口 API-AREA-001~005（详见《库位管理接口文档》）。
 * 全部需 JWT 认证；查询类无按钮权限（结果按数据仓库权限过滤），
 * 写操作按 sys:area:* 校验。
 */
@RestController
@RequestMapping("/api/areas")
@RequiredArgsConstructor
public class AreaController {

    private final AreaService areaService;

    /** AREA-001 库区分页查询（菜单权限控制页面访问，无按钮权限）。 */
    @GetMapping
    public Result<PageResult<AreaVO>> page(@Valid AreaPageQueryDTO dto) {
        return Result.success(areaService.page(dto));
    }

    /** AREA-005 库区下拉选项（字面量路径优先于 /{id} 匹配）。 */
    @GetMapping("/options")
    public Result<List<AreaOptionVO>> options(@RequestParam Long warehouseId,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer status) {
        return Result.success(areaService.options(warehouseId, keyword, status));
    }

    /** AREA-002 新增库区（同仓库编码/名称唯一，areaType 中文标签）。 */
    @PostMapping
    @PreAuthorize("@perm.has('sys:area:add')")
    @OperationLog(module = "库区管理", title = "新增库区")
    public Result<Long> create(@RequestBody @Valid AreaCreateDTO dto) {
        return Result.success(areaService.create(dto));
    }

    /** AREA-003 修改库区（有库存时禁止变更所属仓库 40901）。 */
    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:area:edit')")
    @OperationLog(module = "库区管理", title = "修改库区")
    public Result<Void> update(@PathVariable Long id, @RequestBody @Valid AreaUpdateDTO dto) {
        areaService.update(id, dto);
        return Result.success();
    }

    /** AREA-004 删除库区（存在下级货架/库存引用时 40901，逻辑删除）。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:area:delete')")
    @OperationLog(module = "库区管理", title = "删除库区")
    public Result<Void> delete(@PathVariable Long id) {
        areaService.delete(id);
        return Result.success();
    }
}

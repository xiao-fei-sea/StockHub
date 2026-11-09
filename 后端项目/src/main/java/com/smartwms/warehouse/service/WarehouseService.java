package com.smartwms.warehouse.service;

import com.smartwms.vo.PageResult;
import com.smartwms.warehouse.dto.WarehouseCreateDTO;
import com.smartwms.warehouse.dto.WarehousePageQueryDTO;
import com.smartwms.warehouse.dto.WarehouseUpdateDTO;
import com.smartwms.warehouse.vo.WarehouseOptionVO;
import com.smartwms.warehouse.vo.WarehouseVO;

import java.util.List;

/**
 * 仓库管理服务（API-WH-001~006）。
 * 业务规则见《仓库管理接口文档》：warehouseCode 唯一 40904、
 * 数据仓库权限过滤（非 admin/ROLE_VIEWER 仅见授权仓库，越权 40302）、
 * 删除前置校验（库区/库存/单据引用 40901）。
 */
public interface WarehouseService {

    /** WH-001 分页查询：关键字模糊(code/name/address) + status 过滤，sort/id 升序，叠加数据仓库权限。 */
    PageResult<WarehouseVO> page(WarehousePageQueryDTO dto);

    /** WH-002 新增：编码唯一 40904，status 默认 1、sort 默认 0，返回新仓库 id。 */
    Long create(WarehouseCreateDTO dto);

    /** WH-003 详情：不存在 404，超出数据权限 40302。 */
    WarehouseVO detail(Long id);

    /** WH-004 修改：编码唯一（排除自身）40904，不存在 404，越权 40302。 */
    void update(Long id, WarehouseUpdateDTO dto);

    /** WH-005 逻辑删除：存在库区/库存/单据引用 40901，不存在 404，越权 40302。 */
    void delete(Long id);

    /** WH-006 下拉选项：默认仅 status=1，code/name 模糊，不分页，叠加数据仓库权限。 */
    List<WarehouseOptionVO> options(String keyword, Integer status);
}

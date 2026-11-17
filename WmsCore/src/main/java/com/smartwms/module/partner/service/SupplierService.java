package com.smartwms.module.partner.service;

import com.smartwms.module.partner.dto.SupplierCreateDTO;
import com.smartwms.module.partner.dto.SupplierPageQueryDTO;
import com.smartwms.module.partner.dto.SupplierUpdateDTO;
import com.smartwms.module.partner.vo.SupplierOptionVO;
import com.smartwms.module.partner.vo.SupplierVO;
import com.smartwms.common.PageResult;

import java.util.List;

/** 供应商服务（API-SUP-001~005）。 */
public interface SupplierService {

    /** SUP-001 供应商分页查询（keyword 匹配编码/名称/联系人，按 id 降序）。 */
    PageResult<SupplierVO> page(SupplierPageQueryDTO dto);

    /** SUP-002 新增供应商（supplierCode 全局唯一 40904，电话/邮箱格式 40001），返回新 id。 */
    Long create(SupplierCreateDTO dto);

    /** SUP-003 修改供应商（编码唯一排除自身）。 */
    void update(Long id, SupplierUpdateDTO dto);

    /** SUP-004 删除供应商（存在入库单/批次引用时 40901，逻辑删除）。 */
    void delete(Long id);

    /** SUP-005 供应商下拉选项（默认仅正常，最多 200 条）。 */
    List<SupplierOptionVO> options(String keyword, Integer status);
}

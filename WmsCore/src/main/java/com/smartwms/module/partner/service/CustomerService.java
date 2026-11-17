package com.smartwms.module.partner.service;

import com.smartwms.module.partner.dto.CustomerCreateDTO;
import com.smartwms.module.partner.dto.CustomerPageQueryDTO;
import com.smartwms.module.partner.dto.CustomerUpdateDTO;
import com.smartwms.module.partner.vo.CustomerOptionVO;
import com.smartwms.module.partner.vo.CustomerVO;
import com.smartwms.common.PageResult;

import java.util.List;

/** 客户服务（API-CUS-001~005）。 */
public interface CustomerService {

    /** CUS-001 客户分页查询（keyword 匹配编码/名称/联系人，按 id 降序）。 */
    PageResult<CustomerVO> page(CustomerPageQueryDTO dto);

    /** CUS-002 新增客户（customerCode 全局唯一 40904），返回新 id。 */
    Long create(CustomerCreateDTO dto);

    /** CUS-003 修改客户（编码唯一排除自身）。 */
    void update(Long id, CustomerUpdateDTO dto);

    /** CUS-004 删除客户（存在出库单引用时 40901，逻辑删除）。 */
    void delete(Long id);

    /** CUS-005 客户下拉选项（默认仅正常，最多 200 条）。 */
    List<CustomerOptionVO> options(String keyword, Integer status);
}

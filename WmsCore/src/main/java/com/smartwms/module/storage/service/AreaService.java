package com.smartwms.module.storage.service;

import com.smartwms.module.storage.dto.AreaCreateDTO;
import com.smartwms.module.storage.dto.AreaPageQueryDTO;
import com.smartwms.module.storage.dto.AreaUpdateDTO;
import com.smartwms.module.storage.vo.AreaOptionVO;
import com.smartwms.module.storage.vo.AreaVO;
import com.smartwms.common.PageResult;

import java.util.List;

/** 库区管理服务（API-AREA-001~005）。 */
public interface AreaService {

    /** AREA-001 库区分页查询（按数据仓库权限过滤，按 id 升序）。 */
    PageResult<AreaVO> page(AreaPageQueryDTO dto);

    /** AREA-002 新增库区（areaCode 同仓库唯一 40904，areaType 非法 40001），返回新库区 id。 */
    Long create(AreaCreateDTO dto);

    /** AREA-003 修改库区（编码唯一排除自身；有库存时禁止变更所属仓库 40901）。 */
    void update(Long id, AreaUpdateDTO dto);

    /** AREA-004 删除库区（存在下级货架/库存引用时 40901，逻辑删除）。 */
    void delete(Long id);

    /** AREA-005 库区下拉选项（warehouseId 必填 40001，默认仅正常库区，不分页）。 */
    List<AreaOptionVO> options(Long warehouseId, String keyword, Integer status);
}

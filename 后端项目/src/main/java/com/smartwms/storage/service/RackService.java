package com.smartwms.storage.service;

import com.smartwms.storage.dto.RackCreateDTO;
import com.smartwms.storage.dto.RackPageQueryDTO;
import com.smartwms.storage.dto.RackUpdateDTO;
import com.smartwms.storage.vo.RackVO;
import com.smartwms.vo.PageResult;

/** 货架管理服务（API-RACK-001~004）。 */
public interface RackService {

    /** RACK-001 货架分页查询（warehouseId/areaId 组合须匹配 40001，按数据仓库权限过滤）。 */
    PageResult<RackVO> page(RackPageQueryDTO dto);

    /** RACK-002 新增货架（rackCode 同库区唯一 40904），返回新货架 id。 */
    Long create(RackCreateDTO dto);

    /** RACK-003 修改货架（已有货位禁止跨库区/缩容 40901）。 */
    void update(Long id, RackUpdateDTO dto);

    /** RACK-004 删除货架（存在货位/库存引用时 40901，逻辑删除）。 */
    void delete(Long id);
}

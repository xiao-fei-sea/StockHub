package com.smartwms.module.storage.service;

import com.smartwms.module.storage.dto.LocationBatchGenerateDTO;
import com.smartwms.module.storage.dto.LocationCreateDTO;
import com.smartwms.module.storage.dto.LocationPageQueryDTO;
import com.smartwms.module.storage.dto.LocationUpdateDTO;
import com.smartwms.module.storage.vo.LocationBatchResultVO;
import com.smartwms.module.storage.vo.LocationOptionVO;
import com.smartwms.module.storage.vo.LocationVO;
import com.smartwms.common.PageResult;

import java.util.List;

/** 货位管理服务（API-LOC-001~006）。 */
public interface LocationService {

    /** LOC-001 货位分页查询（层级自洽 40001，按 sort/id 升序，按数据仓库权限过滤）。 */
    PageResult<LocationVO> page(LocationPageQueryDTO dto);

    /** LOC-002 新增货位（locationCode 全局唯一 40904，人工不可置「占用」40901），返回新货位 id。 */
    Long create(LocationCreateDTO dto);

    /** LOC-003 修改货位（有库存禁改层级 40901、禁置禁用/停用 40906）。 */
    void update(Long id, LocationUpdateDTO dto);

    /** LOC-004 删除货位（存在库存/单据明细引用时 40901，逻辑删除）。 */
    void delete(Long id);

    /** LOC-005 货位下拉选项（默认仅正常+空闲货位，不分页，最多 500 条）。 */
    List<LocationOptionVO> options(Long warehouseId, Long areaId, Long rackId,
                                   String locationType, String currentStatus, String keyword);

    /** LOC-006 批量生成货位（按货架层数×列数，单次 ≤500，单事务整体回滚）。 */
    LocationBatchResultVO batchGenerate(LocationBatchGenerateDTO dto);
}

package com.smartwms.module.log.service;

import com.smartwms.module.log.dto.LogPageQueryDTO;
import com.smartwms.module.log.vo.LogVO;
import com.smartwms.common.PageResult;

/**
 * 操作日志查询服务（API-OPL-001/002，详见《16. 操作日志接口文档》）。
 * 只读：日志由 OperationLogAspect 切面写入，接口不提供新增/修改/删除；
 * 与 OperLogService（切面写入通道）分离，业务代码仍不得直接写 sys_oper_log。
 */
public interface LogQueryService {

    /** OPL-001 操作日志分页查询（模块/标题/操作人/状态/HTTP 方法/关键字/日期多条件）。 */
    PageResult<LogVO> page(LogPageQueryDTO dto);

    /** OPL-002 操作日志详情（不存在 40401，越权 40302）。 */
    LogVO detail(Long id);
}

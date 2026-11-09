package com.smartwms.log.service;

import com.smartwms.entity.SysOperLog;

/**
 * 操作日志服务（log 模块对外接口）：仅由 OperationLogAspect 调用，
 * 业务代码不得直接调用或手写插入 sys_oper_log。
 */
public interface OperLogService {

    /** 记录一条操作日志（失败仅告警，不影响主流程）。 */
    void record(SysOperLog operLog);
}

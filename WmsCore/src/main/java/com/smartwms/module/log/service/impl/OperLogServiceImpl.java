package com.smartwms.module.log.service.impl;

import com.smartwms.entity.SysOperLog;
import com.smartwms.module.log.mapper.SysOperLogMapper;
import com.smartwms.module.log.service.OperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 操作日志服务实现：落库失败仅告警，不阻断主业务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperLogServiceImpl implements OperLogService {

    private final SysOperLogMapper operLogMapper;

    @Override
    public void record(SysOperLog operLog) {
        try {
            operLogMapper.insert(operLog);
        } catch (Exception e) {
            log.error("操作日志落库失败 module={} title={}", operLog.getModule(), operLog.getTitle(), e);
        }
    }
}

package com.smartwms.module.log.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.entity.SysOperLog;
import com.smartwms.common.enums.ResultCode;
import com.smartwms.common.BizException;
import com.smartwms.module.log.dto.LogPageQueryDTO;
import com.smartwms.module.log.mapper.SysOperLogMapper;
import com.smartwms.module.log.service.LogQueryService;
import com.smartwms.module.log.vo.LogVO;
import com.smartwms.framework.security.LoginUser;
import com.smartwms.framework.security.WarehouseDataPerm;
import com.smartwms.common.PageResult;
import com.smartwms.common.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 操作日志查询服务实现（API-OPL-001/002）。
 *
 * 16.6 待确认项采纳口径：
 * 1. 日志可见范围 = 不受限用户（admin / ROLE_VIEWER，与仓库数据权限同一判定）查看全部，
 *    其余用户仅可查看本人日志（userId 过滤），越权查看详情返回 40302；
 *    「主管可看本仓库成员日志」的仓库成员口径暂不实现，保留待确认。
 * 2. 存储截断长度 = requestParams / responseResult 统一 2000 字符（与切面写入一致）。
 * 3. 日志为只读：不提供新增/修改/删除接口，清理属运维任务（保留期待确认）。
 */
@Service
@RequiredArgsConstructor
public class LogQueryServiceImpl implements LogQueryService {

    private final SysOperLogMapper operLogMapper;
    private final WarehouseDataPerm dataPerm;

    // ==================== OPL-001 分页查询 ====================

    @Override
    public PageResult<LogVO> page(LogPageQueryDTO dto) {
        LocalDate start = parseDate(dto.getStartDate(), "startDate");
        LocalDate end = parseDate(dto.getEndDate(), "endDate");
        if (start != null && end != null && start.isAfter(end)) {
            throw new BizException(ResultCode.PARAM_INVALID, "startDate 不能晚于 endDate");
        }

        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        // 可见范围：不受限全量，受限仅本人
        if (dataPerm.allowedWarehouseIds() != null) {
            wrapper.eq(SysOperLog::getUserId, currentUserId());
        }
        if (StringUtils.hasText(dto.getModule())) {
            wrapper.like(SysOperLog::getModule, dto.getModule().trim());
        }
        if (StringUtils.hasText(dto.getTitle())) {
            wrapper.like(SysOperLog::getTitle, dto.getTitle().trim());
        }
        if (StringUtils.hasText(dto.getUsername())) {
            wrapper.like(SysOperLog::getUsername, dto.getUsername().trim());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(SysOperLog::getStatus, dto.getStatus());
        }
        if (StringUtils.hasText(dto.getRequestMethod())) {
            wrapper.eq(SysOperLog::getRequestMethod, dto.getRequestMethod());
        }
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = dto.getKeyword().trim();
            wrapper.and(w -> w.like(SysOperLog::getTitle, like)
                    .or().like(SysOperLog::getRequestUrl, like)
                    .or().like(SysOperLog::getUsername, like));
        }
        if (start != null) {
            wrapper.ge(SysOperLog::getOperTime, start.atStartOfDay());
        }
        if (end != null) {
            wrapper.lt(SysOperLog::getOperTime, end.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(SysOperLog::getOperTime).orderByDesc(SysOperLog::getId);

        return PageUtils.query(operLogMapper, dto, wrapper, list -> list.stream().map(this::toVO).toList());
    }

    // ==================== OPL-002 详情 ====================

    @Override
    public LogVO detail(Long id) {
        SysOperLog operLog = operLogMapper.selectById(id);
        if (operLog == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "操作日志不存在");
        }
        // 详情可见范围与列表一致：受限用户仅本人日志（userId 为空的系统日志从严拒绝）
        if (dataPerm.allowedWarehouseIds() != null) {
            Long me = currentUserId();
            if (operLog.getUserId() == null || !operLog.getUserId().equals(me)) {
                throw new BizException(ResultCode.NO_WAREHOUSE_PERM, "无日志查看权限");
            }
        }
        return toVO(operLog);
    }

    // ==================== 私有辅助 ====================

    private LogVO toVO(SysOperLog l) {
        return LogVO.builder()
                .id(l.getId())
                .module(l.getModule())
                .title(l.getTitle())
                .method(l.getMethod())
                .requestMethod(l.getRequestMethod())
                .requestUrl(l.getRequestUrl())
                .requestParams(l.getRequestParams())
                .responseResult(l.getResponseResult())
                .status(l.getStatus())
                .errorMsg(l.getErrorMsg())
                .ip(l.getIp())
                .userId(l.getUserId())
                .username(l.getUsername())
                .costTime(l.getCostTime())
                .operTime(l.getOperTime())
                .build();
    }

    /** 解析 yyyy-MM-dd，格式非法返回 40001；空返回 null。 */
    private LocalDate parseDate(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ResultCode.PARAM_INVALID, field + " 格式须为 yyyy-MM-dd");
        }
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }
}

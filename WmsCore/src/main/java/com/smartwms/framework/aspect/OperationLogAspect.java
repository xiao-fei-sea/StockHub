package com.smartwms.framework.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwms.entity.SysOperLog;
import com.smartwms.module.log.service.OperLogService;
import com.smartwms.framework.security.LoginUser;
import com.smartwms.common.SensitiveUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * 操作日志切面（《后端开发规范》第 10.4 节）：
 * 环绕 @OperationLog 标注方法，记录请求、脱敏参数、IP、耗时与结果；
 * 失败（异常）也记录 status=0；写日志失败不影响主流程。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperLogService operLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        SysOperLog operLog = buildBase(operationLog, joinPoint);
        try {
            Object result = joinPoint.proceed();
            operLog.setStatus(1);
            operLog.setResponseResult(maskResponse(result));
            return result;
        } catch (Throwable e) {
            operLog.setStatus(0);
            operLog.setErrorMsg(e.getMessage() == null ? e.getClass().getSimpleName()
                    : truncate(e.getMessage(), 500));
            throw e;
        } finally {
            operLog.setCostTime((int) (System.currentTimeMillis() - start));
            operLog.setOperTime(LocalDateTime.now());
            operLogService.record(operLog);
        }
    }

    private SysOperLog buildBase(OperationLog operationLog, ProceedingJoinPoint joinPoint) {
        SysOperLog operLog = new SysOperLog();
        operLog.setModule(operationLog.module());
        operLog.setTitle(operationLog.title());
        operLog.setMethod(joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "#" + joinPoint.getSignature().getName());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            operLog.setUserId(loginUser.getId());
            operLog.setUsername(loginUser.getUsername());
        } else {
            // 登录等认证尚未建立的场景：从入参 DTO 回退取 username，保证审计日志可追溯操作人
            for (Object arg : joinPoint.getArgs()) {
                if (arg == null) {
                    continue;
                }
                try {
                    Object uname = arg.getClass().getMethod("getUsername").invoke(arg);
                    if (uname != null && !uname.toString().isEmpty()) {
                        operLog.setUsername(uname.toString());
                        break;
                    }
                } catch (NoSuchMethodException ignored) {
                    // 入参无 getUsername 方法，继续检查下一个参数
                } catch (Exception e) {
                    log.warn("操作日志回退取username失败 paramType={}", arg.getClass().getSimpleName(), e);
                    break;
                }
            }
        }

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            operLog.setRequestMethod(request.getMethod());
            operLog.setRequestUrl(request.getRequestURI());
            operLog.setIp(resolveIp(request));
            operLog.setRequestParams(truncate(maskParams(joinPoint.getArgs()), 2000));
        }
        return operLog;
    }

    /** 响应序列化 + 脱敏 + 截断（2000，与 requestParams 一致；失败不影响主流程）。 */
    private String maskResponse(Object result) {
        if (result == null) {
            return null;
        }
        try {
            return truncate(SensitiveUtil.maskJson(objectMapper.writeValueAsString(result)), 2000);
        } catch (Exception e) {
            return "[序列化失败]";
        }
    }

    /** 参数序列化 + 脱敏；跳过 request/response/file 等非业务对象。 */
    private String maskParams(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        Object[] filtered = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a instanceof HttpServletRequest || a instanceof jakarta.servlet.http.HttpServletResponse
                    || a instanceof MultipartFile || a instanceof jakarta.servlet.ServletResponse) {
                filtered[i] = "[servlet-object]";
            } else {
                filtered[i] = a;
            }
        }
        try {
            return SensitiveUtil.maskJson(objectMapper.writeValueAsString(filtered));
        } catch (Exception e) {
            return "[序列化失败]";
        }
    }

    /** 优先 X-Forwarded-For（Nginx 反代），否则取直连地址。 */
    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}

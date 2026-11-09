package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 操作日志表 sys_oper_log（仅 OperationLogAspect 切面写入，业务代码禁止手写插入）。 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模块，如 认证 / 入库 / 出库 */
    private String module;
    /** 操作标题，如 登录成功 / 退出登录 */
    private String title;
    /** 方法，如 AuthController#login */
    private String method;
    private String requestMethod;
    private String requestUrl;
    /** 请求参数（敏感字段已脱敏） */
    private String requestParams;
    private Integer status;
    private String errorMsg;
    private String ip;
    private Long userId;
    private String username;
    /** 耗时（毫秒） */
    private Integer costTime;
    private LocalDateTime operTime;
}

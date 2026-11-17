package com.smartwms.framework.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注于 Controller 方法，由 OperationLogAspect 统一记录到 sys_oper_log。
 * 业务代码禁止手写插入日志表（《后端开发规范》第 10.4 节）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 模块：认证 / 入库 / 出库 / 库存 / 盘点 / 调拨 / 预警 / 系统 */
    String module();

    /** 操作标题，如 登录成功 / 退出登录 / 审核入库单 */
    String title();
}

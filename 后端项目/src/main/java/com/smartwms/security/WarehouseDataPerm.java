package com.smartwms.security;

import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 数据仓库权限组件（基线第 5 节）：供各业务模块查询过滤与越权校验复用。
 * 口径与认证模块一致：LoginUser.warehouseUnrestricted（ROLE_ADMIN、ROLE_VIEWER）不受限，
 * 其余用户仅见 sys_user_warehouse 授权仓库，越权访问返回 40302。
 */
@Component
public class WarehouseDataPerm {

    /**
     * 当前用户的授权仓库集合：不受限返回 null（调用方不加过滤）；
     * 受限返回授权 id 集合（可能为空集，表示无任何仓库可见）。
     */
    public Set<Long> allowedWarehouseIds() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser loginUser)) {
            return Set.of(); // 理论不可达（全部接口需认证），从严按无权限处理
        }
        if (loginUser.isWarehouseUnrestricted()) {
            return null;
        }
        return loginUser.getWarehouseIds() == null ? Set.of() : Set.copyOf(loginUser.getWarehouseIds());
    }

    /** 校验指定仓库的数据权限，越权抛 40302；warehouseId 为 null 时不做校验（由调用方保证必填）。 */
    public void checkWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            return;
        }
        Set<Long> allowed = allowedWarehouseIds();
        if (allowed == null) {
            return; // 不受限
        }
        if (!allowed.contains(warehouseId)) {
            throw new BizException(ResultCode.NO_WAREHOUSE_PERM, "无数据仓库权限");
        }
    }
}

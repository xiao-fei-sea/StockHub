package com.smartwms.system.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 用户仓库数据权限出参（API-USR-010）。
 */
@Data
@Builder
public class UserWarehouseVO {

    private Long userId;
    /** 授权仓库 id 列表（admin/ROLE_VIEWER 按全量口径返回） */
    private List<Long> warehouseIds;
    /** 授权仓库明细 */
    private List<WarehouseBriefVO> warehouses;

    /** 仓库精简信息 */
    @Data
    @Builder
    public static class WarehouseBriefVO {
        private Long id;
        private String warehouseCode;
        private String warehouseName;
    }
}

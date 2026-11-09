package com.smartwms.stats.mapper;

import com.smartwms.stats.dto.BatchFlowStatDTO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 数据统计专用聚合查询（首期实时计算、不引入缓存，15.1 口径总则）。
 * 复杂聚合走 SQL 侧 GROUP BY，简单分组由 Service 层内存聚合。
 */
public interface StatsMapper {

    /**
     * 按仓库范围聚合每个批次的最近一次入库/出库流水时间（对应方向无流水为 null）。
     * 范围口径与列表查询一致：warehouseId 指定优先，空则按授权集合（IN 排除 NULL 仓库行）。
     */
    @Select("""
            <script>
            SELECT warehouse_id AS warehouseId, batch_id AS batchId,
                   MAX(CASE WHEN direction = 'IN' THEN oper_time END) AS lastInboundTime,
                   MAX(CASE WHEN direction = 'OUT' THEN oper_time END) AS lastOutboundTime
              FROM stock_flow
             WHERE deleted = 0 AND batch_id IS NOT NULL
            <if test="warehouseId != null">
               AND warehouse_id = #{warehouseId}
            </if>
            <if test="warehouseId == null and allowed != null and allowed.size() == 0">
               AND 1 = 0
            </if>
            <if test="warehouseId == null and allowed != null and allowed.size() > 0">
               AND warehouse_id IN
               <foreach collection="allowed" item="w" open="(" separator="," close=")">#{w}</foreach>
            </if>
             GROUP BY warehouse_id, batch_id
            </script>
            """)
    List<BatchFlowStatDTO> selectBatchFlowTimes(@Param("warehouseId") Long warehouseId,
                                                @Param("allowed") Collection<Long> allowed);
}

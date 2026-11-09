package com.smartwms.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.StockAlertRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** stock_alert_record 数据访问。 */
public interface StockAlertRecordMapper extends BaseMapper<StockAlertRecord> {

    /**
     * 条件更新处理预警（API-ALR-007 幂等核心）：仅未处理（status=0）可置为已处理，
     * 并原子写入处理人/处理时间/备注；影响 0 行 = 记录不存在或已是「已处理」→ 40901。
     * 记录表无 version 列，以状态条件更新代替乐观锁（文档 14.4-007 说明）。
     */
    @Update("""
            UPDATE stock_alert_record
               SET status = 1,
                   handle_remark = #{handleRemark},
                   handle_user = #{userId},
                   handle_time = #{now},
                   update_by = #{userId},
                   update_time = #{now}
             WHERE id = #{id} AND status = 0 AND deleted = 0
            """)
    int handleIfUnprocessed(@Param("id") Long id,
                            @Param("handleRemark") String handleRemark,
                            @Param("userId") Long userId,
                            @Param("now") LocalDateTime now);
}

package com.smartwms.transfer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwms.entity.TransferOrder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** transfer_order 数据访问。 */
public interface TransferOrderMapper extends BaseMapper<TransferOrder> {

    /**
     * 查询指定前缀的最新单号（生成 DB+日期+3位序号用，如 prefix=DB20261109）。
     * 并发重复由 uk_transfer_no 兜底 → 40904。
     */
    @Select("SELECT MAX(transfer_no) FROM transfer_order WHERE transfer_no LIKE CONCAT(#{prefix}, '%')")
    String selectLatestNo(@Param("prefix") String prefix);

    /**
     * 物理删除主表（单据不做逻辑删除，基线第 6 节；仅草稿经 TRF-005 校验后调用）。
     */
    @Delete("DELETE FROM transfer_order WHERE id = #{id}")
    int deletePhysically(@Param("id") Long id);
}

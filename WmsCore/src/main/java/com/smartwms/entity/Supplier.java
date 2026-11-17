package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 供应商表 supplier（共享基础资料，不做数据仓库权限隔离）。 */
@Data
@TableName("supplier")
public class Supplier {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 供应商编码（全局唯一，S- 前缀建议） */
    private String supplierCode;
    private String supplierName;
    private String contactName;
    private String contactPhone;
    private String email;
    private String address;
    /** 开户行及账号信息 */
    private String bankInfo;
    /** 状态：0 停用 / 1 正常 */
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}

package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客户表 customer（共享基础资料，无 bank_info 字段；不做数据仓库权限隔离）。 */
@Data
@TableName("customer")
public class Customer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户编码（全局唯一，C- 前缀建议） */
    private String customerCode;
    private String customerName;
    private String contactName;
    private String contactPhone;
    private String email;
    private String address;
    /** 状态：0 停用 / 1 正常 */
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}

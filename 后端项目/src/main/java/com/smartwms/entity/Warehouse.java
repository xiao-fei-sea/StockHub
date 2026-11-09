package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 仓库表 warehouse。 */
@Data
@TableName("warehouse")
public class Warehouse {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String warehouseCode;
    private String warehouseName;
    private String address;
    private String contactName;
    private String contactPhone;
    private java.math.BigDecimal areaSize;
    /** 状态：0 停用 / 1 启用 */
    private Integer status;
    private Integer sort;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}

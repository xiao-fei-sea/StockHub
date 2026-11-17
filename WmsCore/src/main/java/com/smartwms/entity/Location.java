package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 货位表 location（四级结构第4级，库存最小管理单元）。 */
@Data
@TableName("location")
public class Location {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;
    private Long areaId;
    private Long rackId;
    /** 货位编码（HW- 前缀，全局唯一） */
    private String locationCode;
    private String locationName;
    /** 货位类型：1存储位/2拣货位/3暂存位/4收货位 */
    private Integer locationType;
    /** 货位容量体积（立方米） */
    private BigDecimal capacityVolume;
    /** 货位状态：1空闲/2占用/3锁定/4禁用 */
    private Integer currentStatus;
    /** 显示顺序 */
    private Integer sort;
    /** 启用状态：0 停用 / 1 正常 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}

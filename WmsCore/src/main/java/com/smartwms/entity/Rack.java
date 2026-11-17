package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 货架表 rack（四级结构第3级）。 */
@Data
@TableName("rack")
public class Rack {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;
    private Long areaId;
    /** 货架编码（JH- 前缀，同库区内唯一） */
    private String rackCode;
    private String rackName;
    /** 货架层数 */
    private Integer floorCount;
    /** 货架列数 */
    private Integer columnCount;
    /** 状态：0 停用 / 1 正常 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}

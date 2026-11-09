package com.smartwms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 库区表 storage_area（仓库→库区→货架→货位 四级结构第2级）。 */
@Data
@TableName("storage_area")
public class StorageArea {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;
    /** 库区编码（QY- 前缀，同仓库内唯一） */
    private String areaCode;
    private String areaName;
    /** 库区类型：1存储区/2拣货区/3暂存区 */
    private Integer areaType;
    /** 状态：0 停用 / 1 正常 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createBy;
    private Long updateBy;
    private Integer deleted;
}

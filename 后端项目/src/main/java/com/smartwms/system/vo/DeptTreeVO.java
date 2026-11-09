package com.smartwms.system.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点出参（API-DEPT-001）：data 为树数组，非分页结构。
 * 停用部门默认返回并以 status=0 标记，由前端置灰。
 */
@Data
public class DeptTreeVO {

    private Long id;
    private Long parentId;
    private String deptName;
    private Integer sort;
    /** 祖先 id 路径（逗号分隔，根为 0） */
    private String ancestors;
    private Integer status;
    private List<DeptTreeVO> children = new ArrayList<>();
}

package com.smartwms.module.system.service;

import com.smartwms.module.system.dto.DeptCreateDTO;
import com.smartwms.module.system.dto.DeptUpdateDTO;
import com.smartwms.module.system.vo.DeptTreeVO;

import java.util.List;

/**
 * 部门管理服务（API-DEPT-001~004）。
 * 业务规则见《用户管理接口文档》第 3 节：同级部门名唯一 40904、成环/有子级/有用户引用 40901、
 * ancestors 服务端维护、调父级时单事务重建整棵子树。
 */
public interface DeptService {

    /** DEPT-001 部门树：sort 升序、id 升序兜底；可选 status 过滤；停用部门仍返回。 */
    List<DeptTreeVO> tree(Integer status);

    /** DEPT-002 新增：同级名唯一；parentId 不存在 404；ancestors 服务端拼接，返回新部门 id。 */
    Long create(DeptCreateDTO dto);

    /** DEPT-003 修改：禁挂自身/后代（成环 40901）；调父级后单事务重建后代 ancestors；同级名唯一。 */
    void update(Long id, DeptUpdateDTO dto);

    /** DEPT-004 逻辑删除：有子级或有用户引用均拒绝（40901）。 */
    void delete(Long id);
}

package com.smartwms.module.system.service;

import com.smartwms.module.system.dto.RoleCreateDTO;
import com.smartwms.module.system.dto.RoleMenuAssignDTO;
import com.smartwms.module.system.dto.RolePageQueryDTO;
import com.smartwms.module.system.dto.RoleUpdateDTO;
import com.smartwms.module.system.vo.RoleDetailVO;
import com.smartwms.module.system.vo.RoleVO;
import com.smartwms.common.PageResult;

/**
 * 角色管理服务（API-ROLE-001~006，详见《角色权限接口文档》第 1 节）。
 */
public interface RoleService {

    /** ROLE-001 分页查询（按 roleSort、id 升序）。 */
    PageResult<RoleVO> page(RolePageQueryDTO dto);

    /** ROLE-002 新增角色，可同时初始化菜单权限集合。 */
    Long create(RoleCreateDTO dto);

    /** ROLE-003 修改角色基本信息（不含 menuIds）。 */
    void update(Long id, RoleUpdateDTO dto);

    /** ROLE-004 删除角色（逻辑删除并清理 sys_role_menu）。 */
    void delete(Long id);

    /** ROLE-005 角色详情（含已授权 menuIds）。 */
    RoleDetailVO detail(Long id);

    /** ROLE-006 分配菜单权限（整体替换 sys_role_menu）。 */
    void assignMenus(Long id, RoleMenuAssignDTO dto);
}

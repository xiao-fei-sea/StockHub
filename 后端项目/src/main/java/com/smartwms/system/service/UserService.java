package com.smartwms.system.service;

import com.smartwms.system.dto.UserCreateDTO;
import com.smartwms.system.dto.UserPageQueryDTO;
import com.smartwms.system.dto.UserResetPasswordDTO;
import com.smartwms.system.dto.UserRoleAssignDTO;
import com.smartwms.system.dto.UserStatusDTO;
import com.smartwms.system.dto.UserUpdateDTO;
import com.smartwms.system.dto.UserWarehouseAssignDTO;
import com.smartwms.vo.PageResult;
import com.smartwms.system.vo.UserDetailVO;
import com.smartwms.system.vo.UserOptionVO;
import com.smartwms.system.vo.UserVO;
import com.smartwms.system.vo.UserWarehouseVO;

import java.util.List;

/**
 * 用户管理服务（API-USR-001~011）。
 * 业务规则见《用户管理接口文档》第 2 节：username 唯一 40904、admin/自身保护 40901、
 * 角色/仓库整体替换单事务、不返回 password。
 */
public interface UserService {

    /** USR-001 分页查询：关键字模糊(username/real_name/phone) + status/deptId 过滤，createTime 倒序。 */
    PageResult<UserVO> page(UserPageQueryDTO dto);

    /** USR-002 新增：唯一/格式/关联存在性校验后单事务写入用户+角色+仓库，返回新用户 id。 */
    Long create(UserCreateDTO dto);

    /** USR-003 详情：含 roleIds/warehouseIds；不存在返回 404。 */
    UserDetailVO detail(Long id);

    /** USR-004 修改基础信息（不动密码/角色/仓库）；username 冲突 40904，不存在 404。 */
    void update(Long id, UserUpdateDTO dto);

    /** USR-005 逻辑删除并清理关联；admin/自身 40901，不存在 404。 */
    void delete(Long id);

    /** USR-006 启用/停用；admin/自身 40901，不存在 404。 */
    void updateStatus(Long id, UserStatusDTO dto);

    /** USR-007 重置密码（BCrypt 覆盖，不回显）。 */
    void resetPassword(Long id, UserResetPasswordDTO dto);

    /** USR-008 分配角色：整体替换 sys_user_role，单事务。 */
    void assignRoles(Long id, UserRoleAssignDTO dto);

    /** USR-009 下拉选项：仅 status=1 用户，username/realName 模糊，不分页。 */
    List<UserOptionVO> options(String keyword);

    /** USR-010 查询用户仓库数据权限（admin/ROLE_VIEWER 全量口径）。 */
    UserWarehouseVO getUserWarehouses(Long userId);

    /** USR-011 分配仓库数据权限：整体替换 sys_user_warehouse，单事务。 */
    void assignWarehouses(Long id, UserWarehouseAssignDTO dto);
}

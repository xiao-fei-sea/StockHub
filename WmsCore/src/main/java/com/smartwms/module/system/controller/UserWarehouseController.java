package com.smartwms.module.system.controller;

import com.smartwms.module.system.service.UserService;
import com.smartwms.module.system.vo.UserWarehouseVO;
import com.smartwms.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户仓库数据权限查询接口 API-USR-010（详见《用户管理接口文档》第 2 节）。
 * 需 JWT 认证；查询无需按钮权限（分配写操作走 /api/users/{id}/warehouses）。
 */
@RestController
@RequestMapping("/api/user-warehouses")
@RequiredArgsConstructor
public class UserWarehouseController {

    private final UserService userService;

    /** USR-010 查询指定用户的授权仓库集合。 */
    @GetMapping("/{userId}")
    public Result<UserWarehouseVO> getUserWarehouses(@PathVariable Long userId) {
        return Result.success(userService.getUserWarehouses(userId));
    }
}

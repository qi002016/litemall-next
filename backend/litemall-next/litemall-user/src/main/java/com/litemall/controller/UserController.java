package com.litemall.controller;


import com.litemall.domain.Result;
import com.litemall.domain.DTO.PasswordUpdateDTO;
import com.litemall.domain.DTO.UserUpdateInfoDTO;
import com.litemall.domain.VO.UserInfoVO;
import com.litemall.service.IUserService;
import com.litemall.utils.UserContext;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 用户表 前端控制器
 * </p>
 *
 * @author qihuang
 * @since 2026-09-20
 */
@RestController
@RequestMapping("/app/user")
@Tag(name = "用户管理", description = "用户管理相关接口")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    /**
     * 获取用户信息
     */
    @GetMapping("/info")
    @Schema(description = "获取用户信息")
    public Result<UserInfoVO> getUserInfo() {
        Long userId = UserContext.getUser();
        UserInfoVO userInfoVO = userService.getUserInfo(userId);
        return Result.ok(userInfoVO);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/info")
    @Schema(description = "更新用户信息")
    public Result<UserInfoVO> updateUserInfo(@RequestBody @Validated UserUpdateInfoDTO dto) {
        UserInfoVO userInfoVO = userService.updateUserInfo(dto);
        return Result.ok(userInfoVO);
    }

    /**
     * 更新用户密码
     */
    @PutMapping("/password")
    @Schema(description = "更新用户密码")
    public Result updatePassword(@RequestBody @Validated PasswordUpdateDTO dto) {
        userService.updatePassword(dto);
        return Result.ok();
    }
}

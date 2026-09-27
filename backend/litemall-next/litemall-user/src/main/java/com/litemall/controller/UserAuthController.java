package com.litemall.controller;

import com.litemall.domain.DTO.CaptchaDTO;
import com.litemall.domain.DTO.UserLoginFormDTO;
import com.litemall.domain.DTO.UserRegisterDTO;
import com.litemall.domain.DTO.WxLoginDTO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.UserLoginVO;
import com.litemall.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/app/auth")
@Tag(name = "用户登录认证", description = "用户登录认证相关接口")
@RequiredArgsConstructor
public class UserAuthController {

    private final IUserService userService;

    /**
     * 登录
     * @param userLoginFormDTO
     * @return
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录接口")
    public Result<UserLoginVO> login(@RequestBody @Validated UserLoginFormDTO userLoginFormDTO) {
        UserLoginVO userLoginVO = userService.login(userLoginFormDTO);
        return Result.ok(userLoginVO);
    }

    /**
     * 微信小程序登录
     * @param dto wx.login() 返回的 code + 用户信息
     * @return token + 用户信息
     */
    @PostMapping("/wx-login")
    @Operation(summary = "微信小程序登录接口(首次登录自动注册)")
    public Result<UserLoginVO> wxLogin(@RequestBody @Validated WxLoginDTO dto) {
        UserLoginVO vo = userService.wxLogin(dto);
        return Result.ok(vo);
    }

    /**
     * 注册
     * @param userRegisterDTO
     * @return
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册接口")
    public Result<UserLoginVO> register(@RequestBody @Validated UserRegisterDTO userRegisterDTO) {
        UserLoginVO userLoginVO = userService.register(userRegisterDTO);
        return Result.ok(userLoginVO);
    }
    /**
     * 获取验证码
     * @param captchaDTO
     * @return
     */

    @PostMapping("/captcha")
    @Operation(summary = "获取验证码接口")
    public Result<String> getCaptcha(@RequestBody @Validated CaptchaDTO captchaDTO) {
        String captcha = userService.getCaptcha(captchaDTO);
        return Result.ok(captcha);
    }
}

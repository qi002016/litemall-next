package com.litemall.controller;

import com.litemall.domain.PageVO;
import com.litemall.domain.dto.AddressInfoDTO;
import com.litemall.domain.dto.UserAdminDTO;
import com.litemall.domain.dto.UserInfoDTO;
import com.litemall.domain.dto.UserStatusDTO;
import com.litemall.service.IAddressService;
import com.litemall.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 用户服务内部接口（供其他服务 Feign 调用, 不走网关鉴权）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@RestController
@RequestMapping("/inner/user")
@RequiredArgsConstructor
@Tag(name = "用户内部接口", description = "供服务间 Feign 调用的用户接口")
public class UserInnerController {

    private final IUserService userService;
    private final IAddressService addressService;

    @Operation(summary = "根据用户ID批量查询用户信息")
    @GetMapping("/list")
    public List<UserInfoDTO> listUserByIds(@RequestParam("ids") List<Long> ids) {
        return userService.listUserByIds(ids);
    }

    @Operation(summary = "根据地址ID查询收货地址")
    @GetMapping("/address")
    public AddressInfoDTO getAddressById(@RequestParam("id") Long id) {
        return addressService.getAddressInfo(id);
    }

    /* ==================== H5：管理端用户内部接口（admin 服务 Feign 调用） ==================== */

    @Operation(summary = "管理端用户分页列表")
    @GetMapping("/admin/list")
    public PageVO<UserAdminDTO> adminList(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                          @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                          @RequestParam(value = "username", required = false) String username) {
        return userService.adminListUsers(pageNum, pageSize, username);
    }

    @Operation(summary = "管理端用户总数")
    @GetMapping("/admin/count")
    public Long adminCount() {
        return userService.adminCountUsers();
    }

    @Operation(summary = "管理端变更用户状态（0 可用 / 1 禁用 / 2 注销）")
    @PostMapping("/admin/status")
    public void adminUpdateStatus(@RequestBody UserStatusDTO dto) {
        userService.adminUpdateUserStatus(dto);
    }
}

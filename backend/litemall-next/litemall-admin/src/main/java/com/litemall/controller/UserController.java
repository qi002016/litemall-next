package com.litemall.controller;

import com.litemall.client.UserClient;
import com.litemall.domain.DTO.UserStatusReqDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.dto.UserAdminDTO;
import com.litemall.domain.dto.UserStatusDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 管理端用户接口。
 * </p>
 * <p>
 * H5：不再持有 {@code AdminUserMapper}，读写经 {@link UserClient} 调用 user 服务的
 * {@code /inner/user/admin/**}；状态取值（0 可用 / 1 禁用 / 2 注销）由 user 服务内
 * {@code UserStatus} 枚举收口校验。对外路径/方法/字段与改造前一致。
 * </p>
 */
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class UserController {

    private final UserClient userClient;

    @GetMapping("/list")
    public Result<PageVO<UserAdminDTO>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username) {
        return Result.ok(userClient.adminListUsers(pageNum, pageSize, username));
    }

    @PostMapping("/status")
    public Result<Void> status(@Valid @RequestBody UserStatusReqDTO body) {
        UserStatusDTO dto = new UserStatusDTO();
        dto.setId(body.getId());
        dto.setStatus(body.getStatus());
        userClient.adminUpdateUserStatus(dto);
        return Result.ok();
    }
}
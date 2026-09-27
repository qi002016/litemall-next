package com.litemall.client;

import com.litemall.domain.PageVO;
import com.litemall.domain.dto.AddressInfoDTO;
import com.litemall.domain.dto.UserAdminDTO;
import com.litemall.domain.dto.UserInfoDTO;
import com.litemall.domain.dto.UserStatusDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * <p>
 * 用户服务 Feign 客户端
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@FeignClient(name = "litemall-user")
public interface UserClient {

    /**
     * 根据用户ID批量查询用户信息（昵称/头像）
     *
     * @param ids 用户ID集合
     * @return 用户信息列表
     */
    @GetMapping("/inner/user/list")
    List<UserInfoDTO> listUserByIds(@RequestParam("ids") List<Long> ids);

    /**
     * 根据地址ID查询收货地址（下单场景）
     *
     * @param id 地址ID
     * @return 地址信息；不存在返回 null
     */
    @GetMapping("/inner/user/address")
    AddressInfoDTO getAddressById(@RequestParam("id") Long id);

    /* ==================== H5：管理端用户（admin 服务经 Feign 调用，不再直写 litemall_user） ==================== */

    /**
     * 管理端用户分页列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param username 用户名模糊匹配，可空
     * @return 分页用户行
     */
    @GetMapping("/inner/user/admin/list")
    PageVO<UserAdminDTO> adminListUsers(@RequestParam("pageNum") Integer pageNum,
                                        @RequestParam("pageSize") Integer pageSize,
                                        @RequestParam(value = "username", required = false) String username);

    /**
     * 管理端用户总数（首页统计）
     *
     * @return 未删除用户数
     */
    @GetMapping("/inner/user/admin/count")
    Long adminCountUsers();

    /**
     * H5：管理端冻结/解冻/注销用户（状态取值由 user 服务校验：0/1/2）
     *
     * @param dto 用户ID + 目标状态
     */
    @PostMapping("/inner/user/admin/status")
    void adminUpdateUserStatus(@RequestBody UserStatusDTO dto);
}

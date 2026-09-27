package com.litemall.client;

import com.litemall.domain.dto.AddressInfoDTO;
import com.litemall.domain.dto.UserInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
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
}

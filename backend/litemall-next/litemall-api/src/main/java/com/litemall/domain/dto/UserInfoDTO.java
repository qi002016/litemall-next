package com.litemall.domain.dto;

import lombok.Data;

/**
 * <p>
 * 用户信息传输 DTO（Feign 跨服务调用）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Data
public class UserInfoDTO {

    private Long id;

    private String nickname;

    private String avatar;
}

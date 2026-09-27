package com.litemall.domain.dto;

import lombok.Data;

/**
 * <p>
 * 收货地址信息 DTO（Feign 跨服务调用：user → order）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
public class AddressInfoDTO {

    /** 地址ID */
    private Long id;

    /** 收货人姓名 */
    private String name;

    /** 收货人电话 */
    private String mobile;

    /** 省 */
    private String province;

    /** 市 */
    private String city;

    /** 区 */
    private String district;

    /** 详细地址 */
    private String address;

    /** 是否默认 */
    private Boolean isDefault;
}

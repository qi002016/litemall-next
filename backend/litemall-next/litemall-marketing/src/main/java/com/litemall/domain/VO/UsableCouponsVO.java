package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * 可用券试算结果（CN-19）：给定商品金额，返回当前用户的券可用性与可抵扣金额
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "可用券试算结果")
public class UsableCouponsVO {

    @Schema(description = "商品金额（整数分，不含运费）")
    private Integer goodsAmount;

    @Schema(description = "券列表；无券为 []，不会为 null")
    private List<UsableCouponVO> coupons;
}
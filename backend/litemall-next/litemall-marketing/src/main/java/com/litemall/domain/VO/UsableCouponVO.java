package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 可用券试算行（CN-19）：一张「我的券」在指定商品金额下是否可用
 * </p>
 * <p>
 * 金额一律整数分（06 §4：元↔分转换只在 DTO/VO 边界做，字段名不加 Cent 后缀）。
 * </p>
 *
 * @author qihuang
 * @since 2026-11-01
 */
@Data
@Schema(description = "可用券试算行")
public class UsableCouponVO {

    @Schema(description = "用户券记录ID（下单时传该值）", type = "string")
    private Long couponUserId;

    @Schema(description = "优惠券ID", type = "string")
    private Long couponId;

    @Schema(description = "券名称")
    private String name;

    @Schema(description = "券面额（整数分）")
    private Integer discount;

    @Schema(description = "使用门槛（整数分；0 表示无门槛）")
    private Integer min;

    @Schema(description = "券到期时间 yyyy-MM-dd HH:mm:ss；永久券为 null")
    private String endTime;

    @Schema(description = "是否可用")
    private Boolean usable;

    @Schema(description = "不可用原因：未到使用时间/已过期/差 X 元可用/该券已使用/该券已停用；可用为 null")
    private String unusableReason;

    @Schema(description = "本次可抵扣金额（整数分）；不可用为 0，且不超过商品金额")
    private Integer deductAmount;
}
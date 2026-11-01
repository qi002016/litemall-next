package com.litemall.domain.VO;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 领券结果（CI-54 / CI-55）。
 * </p>
 * <p>
 * R2（05 §2.4）四态：{@code data.result ∈ success / already_received / sold_out / disabled}，
 * {@code data.message} 与顶层 {@code msg} 一致；{@code couponUser} 仅在 {@code success} 时存在
 * （失败分支由 {@link JsonInclude.Include#NON_NULL} 省略）。
 * </p>
 * <p>
 * 失败时 {@code Result.code≠200}（400/401/404），是 R1 §4.1「失败 data 只允许 null 或
 * {@code {"errorCode"}}」的显式例外 E-1：四态失败需要 {@code data.result} 供前端区分文案，
 * 该 VO 即失败时的 data 载体。
 * </p>
 *
 * @author qihuang
 * @since 2026-10-08
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "领券结果")
public class CouponReceiveResultVO {

    /** 四态（05 §2.4）：领取成功 */
    public static final String RESULT_SUCCESS = "success";
    /** 四态：该用户已领过该券（含已使用/已过期/已软删） */
    public static final String RESULT_ALREADY_RECEIVED = "already_received";
    /** 四态：券已领完（发放量已用尽） */
    public static final String RESULT_SOLD_OUT = "sold_out";
    /** 四态：券不可领取（停用/不可主动领取/不在有效期） */
    public static final String RESULT_DISABLED = "disabled";

    /** 风险（R1 兼容字段）：仅 CI-55 轮询的「排队中」占位使用 */
    public static final String PROCESSING = "PROCESSING";
    /** R1 兼容字段值：成功 */
    public static final String SUCCESS = "SUCCESS";
    /** R1 兼容字段值：失败 */
    public static final String FAILED = "FAILED";

    @Schema(description = "四态结果：success / already_received / sold_out / disabled")
    private String result;

    @Schema(description = "结果文案（与响应 msg 一致）")
    private String message;

    @Schema(description = "成功时的用户券记录")
    private CouponUserVO couponUser;

    /**
     * R1 兼容字段：R2 起领券同步返回四态，不再有 PROCESSING/FAILED 分支；
     * 该字段只在 CI-55 轮询结果缺失时作为「排队中」占位返回，恒为 null 时不序列化。
     */
    @Deprecated
    @Schema(description = "R1 兼容：PROCESSING/SUCCESS/FAILED（R2 已由 result 取代）", deprecated = true)
    private String status;
}
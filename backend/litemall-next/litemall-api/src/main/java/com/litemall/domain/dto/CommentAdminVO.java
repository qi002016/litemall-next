package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 管理端评价行 VO（05 §3.9 CN-29 {@code GET /admin/comment/list}）。
 * </p>
 * <p>
 * 与 C 端 {@code CommentVO} 的差别（05 §5.7，<b>不得复用同一 SQL</b>）：
 * </p>
 * <ul>
 *   <li>管理端 {@code total} 与列表 <b>含已隐藏（status=1）</b>，C 端只算 {@code status=0}；</li>
 *   <li>额外带订单上下文（{@code orderId/orderSn/orderGoodsId}）与商品名，便于客服定位；</li>
 *   <li>{@code statusText} 固定文案「显示」/「已隐藏」；{@code replied} 由 {@code adminContent} 是否非空派生。</li>
 * </ul>
 * <p>
 * 约定：{@code orderId/orderGoodsId} 可为 null（R1 存量评价未锚订单行）；{@code nickname/avatar}
 * 取不到时为 null，<b>不编造</b>；{@code picUrls} 解析失败按空数组。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端评价VO")
public class CommentAdminVO {

    @Schema(description = "评价ID", type = "string")
    private Long id;

    @Schema(description = "商品ID", type = "string")
    private Long goodsId;

    @Schema(description = "商品名（商品已删除时为 null）")
    private String goodsName;

    @Schema(description = "订单ID", type = "string")
    private Long orderId;

    @Schema(description = "订单号（订单不存在时为 null）")
    private String orderSn;

    @Schema(description = "订单行ID", type = "string")
    private Long orderGoodsId;

    @Schema(description = "评价用户ID", type = "string")
    private Long userId;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "用户头像")
    private String avatar;

    @Schema(description = "评分 1-5")
    private Integer star;

    @Schema(description = "评价内容")
    private String content;

    @Schema(description = "评价图片")
    private List<String> picUrls = new ArrayList<>();

    @Schema(description = "是否含图")
    private Boolean hasPicture;

    @Schema(description = "状态 0=显示 1=已隐藏")
    private Integer status;

    @Schema(description = "状态文案：显示 / 已隐藏")
    private String statusText;

    @Schema(description = "是否已回复")
    private Boolean replied;

    @Schema(description = "商家回复内容")
    private String adminContent;

    @Schema(description = "商家回复时间 yyyy-MM-dd HH:mm:ss")
    private String replyTime;

    @Schema(description = "评价时间 yyyy-MM-dd HH:mm:ss")
    private String addTime;
}
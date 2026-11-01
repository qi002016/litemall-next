package com.litemall.domain.DTO;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * 发表商品评论入参（08 v4 CI-36 {@code POST /app/goods/comment/post}，05 §2.3.1 冻结字段集）。
 * </p>
 * <p>
 * R2 变化（相对 R1）：
 * </p>
 * <ul>
 *   <li>评价<b>锚订单行</b>：{@code orderId} + {@code orderGoodsId} 必填，订单归属与状态由
 *       goods 侧调 CN-IN-01 校验（05 §2.3.3 九步链）；</li>
 *   <li>{@code goodsId} 降级为<b>兼容保留字段</b>——服务端一律取
 *       {@code orderGoods.goodsId}，<b>不采信</b>入参（防伪造评价他人商品）；</li>
 *   <li>{@code content} 由「必填」改为<b>可选</b>（有值才校验 ≤500）；{@code picUrls} 最多 6 张、每张 ≤255。</li>
 * </ul>
 * <p>
 * 这里的 jakarta 注解只做「第一道参数校验」（400 {@code PARAM_INVALID}，即九步链的步 1）；
 * 业务校验（步 2–8）在 {@code CommentServiceImpl} 内按固定顺序执行。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Data
@Schema(description = "发表商品评论DTO")
public class CommentDTO {

    @Schema(description = "订单ID", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @Schema(description = "订单行ID", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "订单行ID不能为空")
    private Long orderGoodsId;

    @Schema(description = "商品ID（兼容保留，服务端以订单行为准，不采信）", type = "string")
    private Long goodsId;

    @Schema(description = "评论内容（可选，最多 500 字）")
    @Size(max = 500, message = "评论内容最多 500 字")
    private String content;

    @Schema(description = "评分 1-5", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分范围为1-5")
    @Max(value = 5, message = "评分范围为1-5")
    private Integer star;

    @Schema(description = "评论图片（最多 6 张）")
    @Size(max = 6, message = "最多上传 6 张图片")
    private List<@Size(max = 255, message = "图片地址最多 255 字符") String> picUrls;
}
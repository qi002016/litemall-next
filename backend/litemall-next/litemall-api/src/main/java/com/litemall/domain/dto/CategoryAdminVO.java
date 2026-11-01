package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 管理端分类树节点（08 v4 CN-21 出参 / 对内 {@code /inner/goods/admin/category/list}）。
 * </p>
 * <p>
 * 与消费端 {@code CategoryVO}（只有 id/name/picUrl/children）<b>不是</b>同一个类型：
 * 管理端需要 {@code pid/level/sortOrder/iconUrl/goodsCount} 做表格展示与层级校验，
 * 但两者都<b>只返回未删除节点</b>。
 * </p>
 * <ul>
 *   <li>{@code level}：R2 口径（05 §1.5.1）取 {@code "1"} / {@code "2"}，由 {@code pid} 推导，
 *       兼容库内历史值 {@code L1/L2}；</li>
 *   <li>{@code goodsCount}：字符串形式的计数（与全局 ID 口径一致），
 *       仅在 {@code withGoodsCount=true} 时有值，否则为 {@code null}（不是 0）；</li>
 *   <li>{@code children}：永远不为 {@code null}，叶子节点为 {@code []}；最多两级。</li>
 * </ul>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "管理端分类树节点")
public class CategoryAdminVO {

    @Schema(description = "分类ID", type = "string")
    private Long id;

    @Schema(description = "父分类ID，顶级为 0", type = "string")
    private Long pid;

    @Schema(description = "分类名称")
    private String name;

    @Schema(description = "层级：1=一级，2=二级")
    private String level;

    @Schema(description = "排序值，越小越靠前")
    private Integer sortOrder;

    @Schema(description = "分类图片")
    private String picUrl;

    @Schema(description = "分类图标")
    private String iconUrl;

    @Schema(description = "该分类下商品数（含已下架，不含已删除）；未请求时为 null", type = "string")
    private String goodsCount;

    @Schema(description = "子分类（最多两级）")
    private List<CategoryAdminVO> children = new ArrayList<>();
}
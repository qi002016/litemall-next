package com.litemall.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 商品货品信息 DTO（Feign 跨服务调用）
 * </p>
 *
 * <p>
 * R1（08 v4 CI-40）新增 {@code skuCode} / {@code specValueIds} / {@code sellable} 三个字段：
 * 下单侧不再靠规格文本猜测组合，可售量与商品域口径统一（number − locked）。
 * 存量字段保持不变（尤其 {@code price} 仍为「元」的 BigDecimal），避免破坏 order 侧既有消费方。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-24
 */
@Data
public class GoodsProductInfoDTO {

    /** 货品ID */
    private Long id;

    /** 商品ID */
    private Long goodsId;

    /** 商品名 */
    private String goodsName;

    /** 商品编号 */
    private String goodsSn;

    /** 商品封面图 */
    private String picUrl;

    /** 货品价格（元；R1 不改语义，避免破坏 order 侧既有消费方） */
    private BigDecimal price;

    /** 规格值数组，JSON */
    private String specifications;

    /** 货品图 */
    private String url;

    /** SKU 稳定业务码（BR-GOODS-07），格式 goodsId-specValueId 升序以下划线连接 */
    private String skuCode;

    /** 规格值 ID 升序数组（BR-GOODS-02），无规格货品为空数组 */
    private List<String> specValueIds;

    /**
     * 可售数量 = max(0, number − locked)。
     * <p>
     * {@code locked} 来自 order 域 CN-IN-04；order 不可达时为 {@code null}（未知，不得当 0）。
     */
    private Integer sellable;

    /**
     * 总库存（number），仅供展示/对账参考；下单可用量一律以 {@link #sellable} 为准。
     */
    private Integer number;
}
package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 足迹VO（4.14）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "足迹VO")
public class FootprintVO {

    @Schema(description = "足迹记录ID", type = "string")
    private Long id;

    @Schema(description = "商品ID", type = "string")
    private Long goodsId;

    @Schema(description = "商品名")
    private String goodsName;

    @Schema(description = "商品主图")
    private String picUrl;

    @Schema(description = "浏览时间（yyyy-MM-dd HH:mm:ss）")
    private String addTime;
}

package com.litemall.domain.VO;

import com.litemall.domain.dto.GoodsBriefDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 专题详情出参（R3-02 D7）。
 * goodsList 直接复用 GoodsBriefDTO：另一个 agent 往该 DTO 加 price/onSale 后，
 * 前端即可拿到价格与上下架态，本模块不需要再映射一遍字段。
 */
@Data
@Schema(description = "专题详情VO")
public class TopicDetailVO {

    @Schema(description = "专题ID", type = "string")
    private Long id;

    @Schema(description = "专题标题")
    private String title;

    @Schema(description = "子标题")
    private String subtitle;

    @Schema(description = "专题封面图")
    private String picUrl;

    @Schema(description = "专题内容（富文本）")
    private String content;

    @Schema(description = "专题商品列表（含 price/onSale）")
    private List<GoodsBriefDTO> goodsList;
}

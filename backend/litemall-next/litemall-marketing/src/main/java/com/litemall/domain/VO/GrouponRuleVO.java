package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "团购活动列表项")
public class GrouponRuleVO {

    @Schema(description = "团购规则 ID")
    private Long id;

    @Schema(description = "商品 ID")
    private Long goodsId;

    @Schema(description = "商品名称")
    private String goodsName;

    @Schema(description = "商品主图")
    private String picUrl;

    @Schema(description = "团购优惠金额（直减）")
    private BigDecimal discount;

    @Schema(description = "成团人数")
    private Integer discountMember;

    @Schema(description = "团购截止时间（yyyy-MM-dd HH:mm:ss）")
    private String expireTime;
}

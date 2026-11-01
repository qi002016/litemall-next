package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单时间线节点（CI-84 {@code timeline[]}，07 §2.4）。
 */
@Data
@Schema(description = "订单时间线节点")
public class OrderTimelineVO {

    @Schema(description = "发生时间（yyyy-MM-dd HH:mm:ss）")
    private String time;

    @Schema(description = "操作者类型：user / admin / system")
    private String actor;

    @Schema(description = "操作者文案")
    private String actorText;

    @Schema(description = "动作文案，如「提交订单」「支付成功」「已发货」「确认收货」「订单关闭」")
    private String action;
}
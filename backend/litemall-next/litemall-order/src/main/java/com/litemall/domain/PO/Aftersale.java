package com.litemall.domain.PO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 售后表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_aftersale")
@Schema(description = "售后表")
public class Aftersale implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "售后编号")
    private String aftersaleSn;

    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "售后类型：0 未收货退款；1 已收货(无需退货)退款；2 用户退货退款")
    private Integer type;

    @Schema(description = "退款原因")
    private String reason;

    @Schema(description = "退款金额")
    private BigDecimal amount;

    @Schema(description = "退款凭证图片链接数组，JSON")
    private String pictures;

    @Schema(description = "退款说明")
    private String comment;

    @Schema(description = "售后状态：0 可申请；1 用户已申请；2 审核通过；3 退款成功；4 审核拒绝；5 用户已取消")
    private Integer status;

    @Schema(description = "管理员操作时间")
    private LocalDateTime handleTime;

    @Schema(description = "订单行（BR-AS-01）")
    private Long orderGoodsId;

    @Schema(description = "申请退款数量（BR-AS-02）")
    private Integer number;

    @Schema(description = "退货物流公司（BR-AS-08）")
    private String returnShipCompany;

    @Schema(description = "退货物流单号（BR-AS-08）")
    private String returnShipSn;

    @Schema(description = "用户填单号时间（BR-AS-08）")
    private LocalDateTime returnShipTime;

    @Schema(description = "运营确认收到退货时间（BR-AS-08/09）")
    private LocalDateTime receiveTime;

    @Schema(description = "关闭/驳回原因（BR-AS-10/22）")
    private String closeReason;

    @Schema(description = "审核操作人 admin.id（BR-ADM-03）")
    private Long auditAdmin;

    @Schema(description = "库存回补状态：0 未回补 / 1 已回补 / 2 待回补（BR-AS-12/16）")
    private Integer restockStatus;

    @Schema(description = "待寄回截止 = 同意时间 + 7 天（BR-AS-10）")
    private LocalDateTime returnDeadline;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;
}

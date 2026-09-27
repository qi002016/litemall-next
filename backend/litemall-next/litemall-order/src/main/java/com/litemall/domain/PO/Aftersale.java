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
    private Integer id;

    @Schema(description = "售后编号")
    private String aftersaleSn;

    @Schema(description = "订单ID")
    private Integer orderId;

    @Schema(description = "用户ID")
    private Integer userId;

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

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;
}

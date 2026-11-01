package com.litemall.domain.DTO;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后关联退款单摘要（只读 {@code litemall_refund}）。
 * <p>
 * 35 列 schema 中 {@code litemall_aftersale} 没有 {@code refund_id} 列，
 * 因此 07 §4.3/§4.7/§4.12 要求的 {@code refundId / refundTime} 由本只读查询取得（pay 写、order 读）。
 * </p>
 */
@Data
public class RefundBriefDTO {

    /** {@code litemall_refund.id} */
    private Long refundId;

    /** 退款完成时间 */
    private LocalDateTime refundTime;

    /** 0 处理中 / 1 成功 / 2 失败 */
    private Integer status;
}

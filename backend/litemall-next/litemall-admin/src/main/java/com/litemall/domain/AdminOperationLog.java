package com.litemall.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 管理端操作日志（litemall_log）。
 * </p>
 * <p>
 * H5：{@code litemall_log} 同为 admin 域自有表（全仓仅本模块读写），
 * 由 admin 自己写入是正确归属；原 {@code AdminLog} 实体已删除，
 * 仅保留本语义明确的自有持久化类型。
 * </p>
 *
 * @since 2026-09-27
 */
@Data
@TableName("litemall_log")
public class AdminOperationLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人账号名 */
    private String admin;

    private String ip;

    private Integer type;

    private String action;

    /** 1 成功，0 失败 */
    private Integer status;

    private String result;

    private String comment;

    /** 操作对象类型（BR-ADM-03） */
    private String targetType;

    /** 操作对象 ID（BR-ORDER-13） */
    private Long targetId;

    private LocalDateTime addTime;

    private LocalDateTime updateTime;

    private Boolean deleted;
}
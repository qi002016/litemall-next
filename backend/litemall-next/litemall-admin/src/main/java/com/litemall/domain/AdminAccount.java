package com.litemall.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 管理端账号（litemall_admin）。
 * </p>
 * <p>
 * H5：{@code litemall_admin} 是 <b>admin 域自有表</b>（全仓仅本模块读写，无其他服务持有它的数据），
 * 因此本模块保留直连访问是数据所有权的正确形态；这里把原 {@code Admin} 实体替换为
 * 语义明确的自有持久化类型，admin 对其余域（order/goods/coupon/ad/topic/user）的表已全部改走 Feign。
 * </p>
 *
 * @since 2026-09-27
 */
@Data
@TableName("litemall_admin")
public class AdminAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String avatar;

    private String roleIds;

    /** 账号状态：1 启用 / 0 禁用（需求 5.14） */
    private Integer status;

    /**
     * 状态文案：启用 / 禁用（05-R2 实施口径 §1.5.1「后台账号出参附 statusText」）。
     * <p>仅用于出参展示，不是 {@code litemall_admin} 的列。</p>
     */
    @TableField(exist = false)
    private String statusText;

    /**
     * 旧密码：仅用于 CI-03 自助改密（{@code PUT /admin/account/update}）时的原密码校验，
     * 不落库、不出参、不参与任何 SQL。
     * <p>管理员改他人密码仍走 CI-04 {@code /reset-password}；本字段只在
     * {@code id == 当前登录账号} 且 {@code password} 非空时被读取。</p>
     */
    @TableField(exist = false)
    private String oldPassword;

    private LocalDateTime addTime;

    private LocalDateTime updateTime;

    private Boolean deleted;
}
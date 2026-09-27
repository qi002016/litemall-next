package com.litemall.domain;

import com.baomidou.mybatisplus.annotation.IdType;
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
    private Integer id;

    private String username;

    private String password;

    private String avatar;

    private String roleIds;

    private LocalDateTime addTime;

    private LocalDateTime updateTime;

    private Boolean deleted;
}
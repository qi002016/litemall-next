package com.litemall.domain.PO;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDate;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 用户表
 * </p>
 *
 * @author qihuang
 * @since 2026-09-20
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_user")
@Schema(description="用户表")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "用户名称")
    private String username;

    @Schema(description = "用户密码")
    private String password;

    @Schema(description = "性别：0 未知， 1男， 1 女")
    private Integer gender;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Schema(description = "最近一次登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "最近一次登录IP地址")
    private String lastLoginIp;

    @Schema(description = "0 普通用户，1 VIP用户，2 高级VIP用户")
    private Integer userLevel;

    @Schema(description = "用户昵称或网络名称")
    private String nickname;

    @Schema(description = "用户手机号码")
    private String mobile;

    @Schema(description = "用户头像图片")
    private String avatar;

    @Schema(description = "微信登录openid")
    private String weixinOpenid;

    @Schema(description = "微信登录会话KEY")
    private String sessionKey;

    @Schema(description = "0 可用, 1 禁用, 2 注销")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDateTime addTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "逻辑删除")
    private Boolean deleted;


}

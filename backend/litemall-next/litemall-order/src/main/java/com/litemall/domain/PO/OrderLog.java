package com.litemall.domain.PO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志（表 {@code litemall_log}，admin 域建的公共日志表；R1 零加列）。
 * <p>
 * order 服务用它承载 07 §3 要求的运营操作留痕：
 * <ul>
 *   <li><b>CN-10 运营关单</b>：{@code action=order:close}，{@code target_type=order}，
 *       {@code target_id=orderId}，{@code admin}=adminId；</li>
 *   <li><b>CN-11 超时关单</b>：{@code action=order:close-expired}（系统发起，admin 为 null）；</li>
 *   <li><b>CN-12 改物流</b>：{@code action=order:ship-update}，{@code result} 记录「旧值 → 新值」。</li>
 * </ul>
 * {@code shipUpdateCount}（CN-33 / P-22）统计的正是本表 {@code action=order:ship-update} 的行数，
 * 因此零加列即可实现「物流单号只能改一次」。
 * </p>
 * <p>
 * 注意：{@code admin} 列是 varchar(45) 的管理员标识；契约里的 {@code adminId} 由 admin 服务
 * 显式透传后写入这里（缺失记 null，<b>禁止伪造 0</b>，P-19）。{@code action} 列 45 字符上限。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("litemall_log")
public class OrderLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 管理员标识（adminId 字符串），系统触发为 null */
    private String admin;

    private String ip;

    private Integer type;

    /** 操作动作，如 order:close / order:close-expired / order:ship-update */
    private String action;

    /** 操作对象类型：order */
    private String targetType;

    /** 操作对象 ID：订单 ID */
    private Long targetId;

    /** 1 成功 0 失败 */
    private Integer status;

    private String result;

    private String comment;

    private LocalDateTime addTime;

    private LocalDateTime updateTime;

    private Boolean deleted;
}
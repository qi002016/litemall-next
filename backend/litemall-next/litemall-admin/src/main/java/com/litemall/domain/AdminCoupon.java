package com.litemall.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("litemall_coupon")
public class AdminCoupon implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String name;
    @TableField("`desc`")
    private String desc;
    private String tag;
    private Integer total;
    private BigDecimal discount;
    @TableField("`min`")
    private BigDecimal min;
    @TableField("`limit`")
    private Integer limit;
    private Integer type;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;
}

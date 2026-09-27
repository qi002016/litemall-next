package com.litemall.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("litemall_order")
public class AdminOrder implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private String orderSn;
    private Integer orderStatus;
    private Integer aftersaleStatus;
    private BigDecimal orderPrice;
    private String shipSn;
    private String shipChannel;
    private LocalDateTime shipTime;
    private LocalDateTime refundTime;
    private LocalDateTime addTime;
    private Boolean deleted;
}

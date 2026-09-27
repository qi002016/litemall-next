package com.litemall.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("litemall_goods")
public class AdminGoods implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String name;
    private String brief;
    private Boolean isOnSale;
    private String picUrl;
    private BigDecimal retailPrice;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;
}

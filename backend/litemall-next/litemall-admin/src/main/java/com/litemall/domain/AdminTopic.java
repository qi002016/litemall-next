package com.litemall.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("litemall_topic")
public class AdminTopic implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String title;
    private String subtitle;
    private String content;
    private BigDecimal price;
    private String readCount;
    private String picUrl;
    private Integer sortOrder;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;
}

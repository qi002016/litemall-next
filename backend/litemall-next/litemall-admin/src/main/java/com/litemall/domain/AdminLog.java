package com.litemall.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("litemall_log")
public class AdminLog implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String admin;
    private String ip;
    private Integer type;
    private String action;
    private Integer status;
    private String result;
    private String comment;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;
}

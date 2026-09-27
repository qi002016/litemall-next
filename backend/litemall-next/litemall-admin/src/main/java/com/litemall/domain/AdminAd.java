package com.litemall.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("litemall_ad")
public class AdminAd implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String name;
    private String link;
    private String url;
    private Integer position;
    private String content;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean enabled;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;
}

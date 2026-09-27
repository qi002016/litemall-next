package com.litemall.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("litemall_admin")
public class Admin {
    private Integer id;
    private String username;
    private String password;
    private String avatar;
    private String roleIds;
    private LocalDateTime addTime;
    private LocalDateTime updateTime;
    private Boolean deleted;
}

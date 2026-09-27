package com.litemall.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.litemall.exception.BadRequestException;

import lombok.Getter;

@Getter
public enum UserStatus {
    NORMAL(0, "正常"),
    FROZEN(1, "禁用"),
    CANCEL(2, "注销");

    @EnumValue
    int value;
    String desc;

    UserStatus(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public static UserStatus of(int value) {
        if (value == 0) {
            return NORMAL;
        }
        if (value == 1) {
            return FROZEN;
        }
        if (value == 2) {
            return CANCEL;
        }
        throw new BadRequestException("账户状态错误");
    }
}
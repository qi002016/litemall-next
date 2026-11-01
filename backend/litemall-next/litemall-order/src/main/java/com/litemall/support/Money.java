package com.litemall.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额换算（R1：库内 {@code decimal(10,2)} 元，出参 int 分；06 §4 / 03 §6.1-7）。
 * <p>
 * 只在 DTO/VO 边界做一次转换，业务计算全程用 {@link BigDecimal} 元，避免反复换算放大误差。
 * </p>
 */
public final class Money {

    private Money() {
    }

    /** 元 → 分（四舍五入到整数分）；null 透传 null */
    public static Integer fen(BigDecimal yuan) {
        if (yuan == null) {
            return null;
        }
        return yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
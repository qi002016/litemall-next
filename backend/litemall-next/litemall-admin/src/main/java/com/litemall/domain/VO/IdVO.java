package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * <p>
 * 新建类接口的返回体（只含新对象ID）。
 * </p>
 * <p>
 * 08 CI-10 / 06 §3.2：新建商品出参固定为 {@code {id}}。全局 {@code JsonConfig} 把
 * {@code Long} 序列化为字符串，因此前端拿到的 {@code id} 是字符串（与列表行一致）。
 * </p>
 */
@Schema(description = "新建对象返回体")
public record IdVO(@Schema(description = "新对象ID") Long id) {

    public static IdVO of(Long id) {
        return new IdVO(id);
    }
}
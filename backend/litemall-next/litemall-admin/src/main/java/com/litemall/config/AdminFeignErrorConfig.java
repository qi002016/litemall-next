package com.litemall.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ConflictException;
import com.litemall.exception.ForbiddenException;
import com.litemall.exception.NotFoundException;
import com.litemall.exception.UnauthorizedException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * <p>
 * admin 侧 Feign 错误解码器（R1 改造：错误码保真）。
 * </p>
 * <p>
 * admin 的业务校验已经从「直写他域表」迁到各服务的 {@code /inner/**} 之后，业务拒绝以
 * 4xx + {@code Result{code,msg,data:{errorCode}}} 返回（见 {@code CommonExceptionAdvice}，
 * 对内端点保留非 200）。Feign 默认解码器会把这类响应包成 {@code FeignException}，最终被 admin
 * 的兜底异常处理转成 HTTP 500「服务器内部异常」，把真正的业务原因吞掉。
 * </p>
 * <p>
 * 改造前的缺陷：所有 4xx 一律压成 400 且丢弃 {@code data.errorCode}，导致
 * 404 {@code RESOURCE_NOT_FOUND}、409 {@code GOODS_IN_USE} / {@code CONCURRENT_MODIFIED} /
 * {@code STOCK_ADJUST_CONFLICT} / {@code AFTERSALE_STATUS_ILLEGAL} 在 admin 对外全部变成
 * 「400 无码」，与 08 v4 / 07 §1.4 的契约不符。现在按状态码还原异常类型，并把
 * {@code data.errorCode} 原样透传（06 §1.2.1：失败响应 {@code data} 只允许 null 或
 * {@code {\"errorCode\"}}）。
 * </p>
 * <p>
 * 仅作用于 admin 自身上下文中的 Feign 客户端；5xx 与无法解析的响应仍按 Feign 默认行为抛出。
 * </p>
 */
@Slf4j
@Configuration
public class AdminFeignErrorConfig {

    @Bean
    public ErrorDecoder adminFeignErrorDecoder() {
        return (methodKey, response) -> {
            int status = response.status();
            if (status < 400 || status >= 500) {
                return new ErrorDecoder.Default().decode(methodKey, response);
            }
            ErrorPayload payload = extractPayload(response);
            if (payload == null || payload.msg() == null) {
                return new ErrorDecoder.Default().decode(methodKey, response);
            }
            log.warn("[R1] 内部接口业务拒绝 -> {} status={} errorCode={} msg={}",
                    methodKey, status, payload.errorCode(), payload.msg());
            return switch (status) {
                case 401 -> new UnauthorizedException(payload.msg());
                case 403 -> new ForbiddenException(payload.msg());
                case 404 -> new NotFoundException(payload.msg(),
                        payload.errorCode() == null ? NotFoundException.RESOURCE_NOT_FOUND : payload.errorCode());
                case 409 -> new ConflictException(payload.msg(), payload.errorCode());
                case 400 -> new BadRequestException(payload.msg(), payload.errorCode());
                default -> new BadRequestException(payload.msg(), payload.errorCode());
            };
        };
    }

    /** 从 {@code Result} 响应体里取 msg 与 data.errorCode；结构不符时返回 null，交回默认解码器 */
    private ErrorPayload extractPayload(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (var in = response.body().asInputStream()) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return null;
            }
            JSONObject json = JSONUtil.parseObj(body);
            String msg = json.getStr("msg");
            if (msg == null || msg.isBlank()) {
                return null;
            }
            String errorCode = null;
            JSONObject data = json.getJSONObject("data");
            if (data != null) {
                String raw = data.getStr("errorCode");
                errorCode = (raw == null || raw.isBlank()) ? null : raw;
            }
            return new ErrorPayload(msg, errorCode);
        } catch (Exception e) {
            log.warn("[R1] 解析内部接口错误响应失败，按默认解码处理", e);
            return null;
        }
    }

    /** 内部错误响应体中的可透传字段 */
    private record ErrorPayload(String msg, String errorCode) {
    }
}
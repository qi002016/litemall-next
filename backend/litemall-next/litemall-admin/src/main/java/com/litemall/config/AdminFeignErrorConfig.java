package com.litemall.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ForbiddenException;
import com.litemall.exception.UnauthorizedException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * <p>
 * H5：admin 侧 Feign 错误解码器。
 * </p>
 * <p>
 * admin 的业务校验已经从"直写他域表"迁到各服务的 {@code /inner/**} 之后，
 * 业务拒绝会以 4xx + {@code Result{code,msg}} 返回（见 {@code CommonExceptionAdvice}）。
 * Feign 默认解码器会把这类响应包成 {@code FeignException}，最终被 admin 的兜底异常处理
 * 转成 HTTP 500「服务器内部异常」，把真正的业务原因（如"仅待发货订单可发货，当前状态：unpaid"）吞掉。
 * 这里把它还原为对应的 {@code CommonException}，让管理端拿到 400 + 原始提示。
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
            String msg = extractMsg(response);
            if (msg == null) {
                return new ErrorDecoder.Default().decode(methodKey, response);
            }
            log.warn("[H5] 内部接口业务拒绝 -> {} status={} msg={}", methodKey, status, msg);
            if (status == 401) {
                return new UnauthorizedException(msg);
            }
            if (status == 403) {
                return new ForbiddenException(msg);
            }
            return new BadRequestException(msg);
        };
    }

    /** 从 {@code Result} 响应体里取 msg；结构不符时返回 null，交回默认解码器 */
    private String extractMsg(Response response) {
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
            return (msg == null || msg.isBlank()) ? null : msg;
        } catch (Exception e) {
            log.warn("[H5] 解析内部接口错误响应失败，按默认解码处理", e);
            return null;
        }
    }
}
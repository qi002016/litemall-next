package com.litemall.advice;

import com.litemall.domain.Result;
import com.litemall.exception.GoodsApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 商品域异常 → 对外响应格式（06 §1.2.1 / 03 §4.1）。
 *
 * <p>
 * R1 冻结口径按端点面分流，与公共层 {@code CommonExceptionAdvice} 完全一致：
 * </p>
 * <ul>
 *   <li><b>对外端点</b>（{@code /app/**}、{@code /admin/**}）：<b>HTTP 恒 200</b>，
 *       业务码放 {@code body.code}，机器可读错误码放 {@code body.data.errorCode}，
 *       且 {@code data} 只允许 {@code null} 或 {@code {"errorCode": "..."}} 两种形态；</li>
 *   <li><b>对内端点</b>（{@code /inner/**}）：<b>保留非 200</b>。Feign 只有看到非 2xx 才会抛
 *       {@code FeignException}，否则调用方会把失败当成功（资损级）。判定按请求 URI 前缀
 *       {@code /inner/} 分流，不靠端点清单维护。</li>
 * </ul>
 *
 * <p>
 * 本 advice 只处理 {@link GoodsApiException}（商品域自有的 {@code errorCode} 载体），
 * 优先级高于公共层 {@code CommonExceptionAdvice}；其它异常类型不在此拦截，仍由公共层处理。
 * </p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GoodsApiExceptionAdvice {

    /** 对内端点前缀：这类端点保留非 200，供 Feign 感知失败 */
    private static final String INNER_PREFIX = "/inner/";

    @ExceptionHandler(GoodsApiException.class)
    public ResponseEntity<Result<Map<String, String>>> handleGoodsApiException(GoodsApiException e,
            HttpServletRequest request) {
        log.warn("商品域业务异常 -> uri={}, code={}, errorCode={}, msg={}",
                request == null ? null : request.getRequestURI(), e.getCode(), e.getErrorCode(), e.getMessage());
        Result<Map<String, String>> body = new Result<>(e.getCode(), e.getMessage(),
                Map.of("errorCode", e.getErrorCode()));
        if (isInnerRequest(request)) {
            return ResponseEntity.status(resolveHttpStatus(e.getCode())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    private boolean isInnerRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith(INNER_PREFIX);
    }

    /** 业务码 → HTTP 状态；非标准 HTTP 码统一落 500，避免非法状态行 */
    private HttpStatus resolveHttpStatus(int code) {
        HttpStatus status = HttpStatus.resolve(code);
        return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }
}
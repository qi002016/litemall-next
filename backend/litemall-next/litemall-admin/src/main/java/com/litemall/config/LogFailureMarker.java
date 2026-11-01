package com.litemall.config;

import com.litemall.exception.CommonException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * <p>
 * 业务失败的「请求标记」（供 {@link OperationLogInterceptor} 判定 D-36 的 {@code status}）。
 * </p>
 * <p>
 * 背景：对外端点（{@code /admin/**}）的异常响应一律 <b>HTTP 200</b>（03 §4.1），真实业务码只在
 * {@code Result.code} 里。因此拦截器在 {@code afterCompletion} 里只看 HTTP 状态码<b>判不出失败</b>
 * ——「订单不存在」这类 400 会被记成 status=0（成功），CI-13 的「失败」筛选就永远是空的。
 * </p>
 * <p>
 * 本 resolver 排在所有 {@code @ExceptionHandler} 解析器之前（{@link #getOrder()} =
 * {@link Ordered#HIGHEST_PRECEDENCE}），只做<h3>观测</h3>：把「本次请求失败」与细分码挂到请求属性上，
 * 随后 {@code return null} 表示<b>不处理</b>，异常继续交给 {@code CommonExceptionAdvice} 生成响应。
 * 这样既不改动统一错误结构，也让日志能如实记 status=1。
 * </p>
 *
 * @since 2026-11-01
 */
@Slf4j
@Component
public class LogFailureMarker implements HandlerExceptionResolver, Ordered {

    /** 请求属性：本次请求是否失败（Boolean.TRUE 表示失败） */
    public static final String ATTR_FAILED = LogFailureMarker.class.getName() + ".failed";

    /** 请求属性：失败细分码（可空，如 {@code RESOURCE_NOT_FOUND}） */
    public static final String ATTR_ERROR_CODE = LogFailureMarker.class.getName() + ".errorCode";

    /** 请求属性：失败业务码（Result.code，如 400/404/500） */
    public static final String ATTR_CODE = LogFailureMarker.class.getName() + ".code";

    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                         Object handler, Exception ex) {
        try {
            request.setAttribute(ATTR_FAILED, Boolean.TRUE);
            if (ex instanceof CommonException common) {
                request.setAttribute(ATTR_CODE, common.getCode());
                if (common.getErrorCode() != null) {
                    request.setAttribute(ATTR_ERROR_CODE, common.getErrorCode());
                }
            }
        } catch (Exception ignored) {
            // 打标记失败绝不影响异常处理链路
        }
        // 不解析：交给后续 @ExceptionHandler 生成统一错误响应
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
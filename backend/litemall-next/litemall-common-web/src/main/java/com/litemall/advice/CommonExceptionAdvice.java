package com.litemall.advice;

import com.litemall.domain.Result;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.CommonException;
import com.litemall.exception.DbException;
import com.litemall.utils.WebUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.BindException;
import java.util.stream.Collectors;

/**
 * 全局异常处理。
 * <p>
 * <b>R1 三层错误结构（03 §4.1/§4.2，冻结）</b>：
 * <ol>
 *   <li><b>对外端点</b>（{@code /app/**}、{@code /admin/**}）异常响应一律 <b>HTTP 200</b>，
 *       真实业务码只放 {@code Result.code}；前端以 {@code Result.code} 分支，
 *       不再依赖 HTTP 状态码（改造前是 {@code ResponseEntity.status(e.getCode())}，与 06 §1.1 冲突）。</li>
 *   <li><b>对内端点</b>（{@code /inner/**}）<b>保留非 200</b>：Feign 只有看到非 2xx 才能抛出
 *       {@code FeignException}，否则调用方会把失败当成功（资损级）。判定按请求 URI 前缀
 *       {@code /inner/} 分流，不靠端点清单维护。</li>
 *   <li>网关鉴权拒绝由 gateway（WebFlux）直接回 HTTP 401/403，不经本 advice。</li>
 * </ol>
 * 失败响应 {@code data} 只允许 {@code null} 或严格 {@code {"errorCode":"XXX"}}（见 {@link Result#error(CommonException)}）。
 * </p>
 */
@RestControllerAdvice
@Slf4j
@ConditionalOnWebApplication(type = Type.SERVLET)
public class CommonExceptionAdvice {

    /** 对内端点前缀：这类端点保留非 200，供 Feign 感知失败 */
    private static final String INNER_PREFIX = "/inner/";

    @ExceptionHandler(DbException.class)
    public Object handleDbException(DbException e) {
        log.error("mysql数据库操作异常 -> ", e);
        return processResponse(e);
    }

    @ExceptionHandler(CommonException.class)
    public Object handleBadRequestException(CommonException e) {
        log.error("自定义异常 -> {} , 异常原因：{}  ", e.getClass().getName(), e.getMessage());
        log.debug("", e);
        return processResponse(e);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors()
                .stream().map(ObjectError::getDefaultMessage)
                .collect(Collectors.joining("|"));
        log.error("请求参数校验异常 -> {}", msg);
        log.debug("", e);
        return processResponse(new BadRequestException(msg, BadRequestException.PARAM_INVALID));
    }

    @ExceptionHandler(BindException.class)
    public Object handleBindException(BindException e) {
        log.error("请求参数绑定异常 ->BindException， {}", e.getMessage());
        log.debug("", e);
        return processResponse(new BadRequestException("请求参数格式错误", BadRequestException.PARAM_INVALID));
    }

    @ExceptionHandler(ServletException.class)
    public Object handleServletException(ServletException e) {
        log.error("参数异常 -> ServletException，{}", e.getMessage());
        log.debug("", e);
        return processResponse(new BadRequestException("请求参数处理异常", BadRequestException.PARAM_INVALID));
    }

    @ExceptionHandler(Exception.class)
    public Object handleRuntimeException(Exception e) {
        HttpServletRequest request = WebUtils.getRequest();
        log.error("其他异常 uri : {} -> ", request == null ? null : request.getRequestURI(), e);
        return processResponse(new CommonException("服务器内部异常", 500));
    }

    /**
     * 按端点面分流：对外恒 200（业务码在 body），对内保留非 200（Feign 依赖）。
     */
    private ResponseEntity<Result<?>> processResponse(CommonException e) {
        Result<?> body = Result.error(e);
        if (isInnerRequest()) {
            return ResponseEntity.status(resolveHttpStatus(e.getCode())).body(body);
        }
        return ResponseEntity.ok(body);
    }

    private boolean isInnerRequest() {
        HttpServletRequest request = WebUtils.getRequest();
        if (request == null) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith(INNER_PREFIX);
    }

    /** 业务码 → HTTP 状态；非标准 HTTP 码（如 429 之外的自定义值）统一落 500，避免非法状态行 */
    private HttpStatus resolveHttpStatus(int code) {
        HttpStatus status = HttpStatus.resolve(code);
        return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }
}
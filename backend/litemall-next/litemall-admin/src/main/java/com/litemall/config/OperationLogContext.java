package com.litemall.config;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * <p>
 * 操作日志的「业务补充信息」传递通道（R1 BR-ADM-03）。
 * </p>
 * <p>
 * {@link OperationLogInterceptor} 统一在 {@code afterCompletion} 写 {@code litemall_log}，
 * 但它不解析请求体，拿不到 {@code targetType/targetId/comment}（例如库存调整的备注与
 * SKU 主键）。控制器在业务成功后调用 {@link #mark(String, String, Long)} 把这三项挂到当前
 * 请求属性上，由拦截器合并写入同一行，避免重复落两条日志。
 * </p>
 * <p>
 * 全部操作为尽力而为：没有请求上下文（如单测直接调控制器）时静默跳过，绝不影响业务响应。
 * </p>
 *
 * @since 2026-11-01
 */
public final class OperationLogContext {

    /** 请求属性键 */
    public static final String ATTR = OperationLogContext.class.getName() + ".extra";

    private OperationLogContext() {
    }

    /**
     * 记录本次请求的业务补充信息（供拦截器合并写入操作日志）。
     *
     * @param comment    日志备注（可空，如库存调整备注）
     * @param targetType 操作对象类型（可空，如 {@code product}）
     * @param targetId   操作对象ID（可空）
     */
    public static void mark(String comment, String targetType, Long targetId) {
        mark(comment, targetType, targetId, null);
    }

    /**
     * 记录本次请求的业务补充信息（含动作文案）。
     * <p>
     * R2（05 §3.14/§5.11）：内容停用类动作的日志 {@code action} 必须是业务文案
     * （如「停用公告」「启用公告」），而非默认的 {@code METHOD path}，因此在此显式透传。
     * </p>
     *
     * @param comment    日志备注（可空）
     * @param targetType 操作对象类型（可空，如 {@code notice}）
     * @param targetId   操作对象ID（可空）
     * @param action     动作文案（可空；为空时拦截器回退为 {@code METHOD path}）
     */
    public static void mark(String comment, String targetType, Long targetId, String action) {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            attributes.setAttribute(ATTR, new Extra(comment, targetType, targetId, action),
                    RequestAttributes.SCOPE_REQUEST);
        } catch (Exception ignored) {
            // 日志补充信息失败不影响业务
        }
    }

    /**
     * 读取当前请求的业务补充信息。
     *
     * @return 补充信息；不存在时返回 null
     */
    public static Extra current() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            Object value = attributes.getAttribute(ATTR, RequestAttributes.SCOPE_REQUEST);
            return value instanceof Extra extra ? extra : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 业务补充信息。
     *
     * @param comment    日志备注
     * @param targetType 操作对象类型
     * @param targetId   操作对象ID
     * @param action     动作文案（可空；为空时拦截器用 {@code METHOD path}）
     */
    public record Extra(String comment, String targetType, Long targetId, String action) {
    }
}
package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.AdminOperationLog;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.AdminOperationLogVO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.AdminOperationLogMapper;
import com.litemall.support.AdminQuerySupport;
import com.litemall.support.LogTargetTypes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * 管理端操作日志查询（CI-13，05-R2 实施口径 §2.5）。
 * </p>
 * <p>
 * 数据源是 admin 域自有表 {@code litemall_log}（admin 是唯一写方，§1.7），因此不新增 Feign、不做跨库直连。
 * 契约要点：
 * </p>
 * <ul>
 *   <li>筛选：{@code admin}（≤32 模糊）、{@code targetType}（十项白名单，非白名单 ⇒ 400）、
 *       {@code targetId}（string(Long) 精确）、{@code action}（≤64 模糊）、{@code status}（{@code "0,1"} CSV）、
 *       {@code dateFrom}/{@code dateTo}（{@code yyyy-MM-dd HH:mm:ss}，格式错或 from &gt; to ⇒ 400）；</li>
 *   <li>排序<b>固定</b> {@code add_time DESC, id DESC}（§2.5.1 注册例外：不提供 sortBy，
 *       避免与后端写入顺序/D-36 语义脱钩）；</li>
 *   <li>出参 {@code PageVO<AdminOperationLogVO>}，含 {@code typeText}/{@code statusText} 展示文案
 *       （D-36：type 1=业务操作/2=登录；status 0=成功/1=失败）；</li>
 *   <li>分页归一走 {@link AdminQuerySupport}（默认 20、上限 100 不报错）。</li>
 * </ul>
 *
 * @since 2026-11-01
 */
@RestController
@RequestMapping("/admin/log")
@RequiredArgsConstructor
@Tag(name = "管理端-操作日志")
public class LogController {

    /** targetType 白名单（§2.5.1 冻结字面量；与写日志共用 {@link LogTargetTypes} 一份定义） */
    public static final Set<String> TARGET_TYPES = LogTargetTypes.WHITELIST;

    /** status 白名单（D-36：0=成功 1=失败） */
    public static final Set<String> LOG_STATUSES = Set.of("0", "1");

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AdminOperationLogMapper logMapper;

    @Operation(summary = "操作日志列表（CI-13）",
            description = "支持操作人/目标类型/目标ID/动作/状态/时间范围筛选；排序固定 add_time DESC, id DESC。")
    @GetMapping("/list")
    public Result<PageVO<AdminOperationLogVO>> list(
            @Parameter(description = "页码，缺省 1") @RequestParam(required = false) Integer pageNum,
            @Parameter(description = "每页条数，缺省 20，上限 100") @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "操作管理员账号（模糊，≤32）") @RequestParam(required = false) String admin,
            @Parameter(description = "目标类型（白名单）") @RequestParam(required = false) String targetType,
            @Parameter(description = "目标ID（精确）") @RequestParam(required = false) String targetId,
            @Parameter(description = "动作（模糊，≤64）") @RequestParam(required = false) String action,
            @Parameter(description = "状态 CSV，0=成功 1=失败") @RequestParam(required = false) String status,
            @Parameter(description = "起始时间 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String dateFrom,
            @Parameter(description = "结束时间 yyyy-MM-dd HH:mm:ss") @RequestParam(required = false) String dateTo) {

        String adminKeyword = AdminQuerySupport.text(admin, 32, "admin");
        String type = parseTargetType(targetType);
        Long target = parseTargetId(targetId);
        String actionKeyword = AdminQuerySupport.text(action, 64, "action");
        List<Integer> statuses = parseStatuses(status);
        LocalDateTime from = parseDateTime(dateFrom, "dateFrom");
        LocalDateTime to = parseDateTime(dateTo, "dateTo");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("dateFrom 不能晚于 dateTo", "PARAM_INVALID");
        }

        LambdaQueryWrapper<AdminOperationLog> query = new LambdaQueryWrapper<AdminOperationLog>()
                .eq(AdminOperationLog::getDeleted, false)
                .like(adminKeyword != null, AdminOperationLog::getAdmin, adminKeyword)
                .eq(type != null, AdminOperationLog::getTargetType, type)
                .eq(target != null, AdminOperationLog::getTargetId, target)
                .like(actionKeyword != null, AdminOperationLog::getAction, actionKeyword)
                .in(!statuses.isEmpty(), AdminOperationLog::getStatus, statuses)
                .ge(from != null, AdminOperationLog::getAddTime, from)
                .le(to != null, AdminOperationLog::getAddTime, to)
                // §2.5.1 排序固定：先按发生时间倒序，同一秒内按自增 ID 倒序，保证翻页稳定
                .orderByDesc(AdminOperationLog::getAddTime)
                .orderByDesc(AdminOperationLog::getId);

        Page<AdminOperationLog> page = logMapper.selectPage(
                new Page<>(AdminQuerySupport.pageNum(pageNum), AdminQuerySupport.pageSize(pageSize)), query);

        return Result.ok(PageVO.of(page, LogController::toVO));
    }

    /**
     * 实体 → 出参：补齐 D-36 展示文案与时间格式。
     */
    static AdminOperationLogVO toVO(AdminOperationLog log) {
        AdminOperationLogVO vo = new AdminOperationLogVO();
        vo.setId(log.getId() == null ? null : String.valueOf(log.getId()));
        vo.setAdmin(log.getAdmin());
        vo.setIp(log.getIp());
        vo.setType(log.getType());
        vo.setTypeText(typeText(log.getType()));
        vo.setAction(log.getAction());
        vo.setStatus(log.getStatus());
        vo.setStatusText(statusText(log.getStatus()));
        vo.setResult(log.getResult());
        vo.setComment(log.getComment());
        vo.setTargetType(log.getTargetType());
        vo.setTargetId(log.getTargetId() == null ? null : String.valueOf(log.getTargetId()));
        vo.setAddTime(log.getAddTime() == null ? null : log.getAddTime().format(DATE_TIME));
        return vo;
    }

    /** D-36：type 1=业务操作 2=登录 */
    static String typeText(Integer type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case 1 -> "业务操作";
            case 2 -> "登录";
            default -> "未知(" + type + ")";
        };
    }

    /** D-36：status 0=成功 1=失败 */
    static String statusText(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case 0 -> "成功";
            case 1 -> "失败";
            default -> "未知(" + status + ")";
        };
    }

    /**
     * targetType 单值白名单校验（大小写不敏感，落库规范小写）。
     */
    static String parseTargetType(String raw) {
        String value = AdminQuerySupport.text(raw, 32, "targetType");
        if (value == null) {
            return null;
        }
        String canonical = LogTargetTypes.normalize(value);
        if (canonical == null) {
            throw new BadRequestException("targetType 取值非法：" + value, "PARAM_INVALID");
        }
        return canonical;
    }

    /**
     * targetId：string(Long) 精确匹配；非数字 ⇒ 400（不允许静默忽略，否则会误返回全量）。
     */
    static Long parseTargetId(String raw) {
        String value = AdminQuerySupport.text(raw, 32, "targetId");
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new BadRequestException("targetId 必须是数字", "PARAM_INVALID");
        }
    }

    /**
     * status CSV：白名单 {0,1}，去重；空 ⇒ 不筛选。
     */
    static List<Integer> parseStatuses(String raw) {
        String csv = AdminQuerySupport.csvTokens(raw, LOG_STATUSES, "status");
        if (csv == null) {
            return List.of();
        }
        Set<Integer> values = new LinkedHashSet<>();
        for (String token : csv.split(",")) {
            values.add(Integer.valueOf(token));
        }
        return new ArrayList<>(values);
    }

    /**
     * 时间入参：复用统一校验（格式错 ⇒ 400），再转 {@link LocalDateTime} 供 Wrapper 使用。
     */
    static LocalDateTime parseDateTime(String raw, String field) {
        String value = AdminQuerySupport.dateTime(raw, field);
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(field + " 格式应为 yyyy-MM-dd HH:mm:ss", "PARAM_INVALID");
        }
    }
}
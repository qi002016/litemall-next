package com.litemall.domain.DTO;

import com.litemall.exception.GoodsApiException;
import com.litemall.support.GoodsSupport;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 管理端评价列表查询入参（05 §3.9 CN-29 / 对内）。
 *
 * <p>
 * 归一策略对齐 {@link GoodsAdminQuery}（05 §1.4）：
 * </p>
 * <ul>
 *   <li>分页：{@code pageNum<1⇒1}、{@code pageSize} 缺省 20 上限 100；</li>
 *   <li>{@code keyword} 去首尾空白，空串⇒不筛，超 50 字⇒400（按 {@code content} 模糊，05 §3.9 优先于 08 的「内容或商品名」）；</li>
 *   <li>{@code star} / {@code status} 支持逗号多值，非数值或越界⇒400 {@code PARAM_INVALID}；</li>
 *   <li>{@code dateFrom/dateTo} 固定 {@code yyyy-MM-dd HH:mm:ss}，格式错⇒400；起 &gt; 止⇒400（顺着传反了不该静默返回空页）。</li>
 * </ul>
 * <p>
 * <b>total 口径</b>：本查询<b>含已隐藏</b>（管理端要能看见全部），C 端 CI-35 只算 {@code status=0}，
 * 两者不得复用同一 SQL（05 §5.7）。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
public class CommentAdminQuery {

    private Integer pageNum;

    private Integer pageSize;

    private Long goodsId;

    private String keyword;

    /** 星级逗号多值（1–5），如 {@code 1,2} */
    private String star;

    /** 是否已回复；null ⇒ 不筛 */
    private Boolean replied;

    /** 状态逗号多值（0=显示 1=已隐藏） */
    private String status;

    /** 评价时间起 {@code yyyy-MM-dd HH:mm:ss} */
    private String dateFrom;

    /** 评价时间止 {@code yyyy-MM-dd HH:mm:ss} */
    private String dateTo;

    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int MAX_PAGE_SIZE = 100;

    public static final int MAX_KEYWORD_LENGTH = 50;

    private static final List<Integer> ALLOWED_STAR = List.of(1, 2, 3, 4, 5);

    private static final List<Integer> ALLOWED_STATUS = List.of(0, 1);

    /** 归一后的星级集合（空 = 不筛） */
    private List<Integer> stars = new ArrayList<>();

    /** 归一后的状态集合（空 = 不筛） */
    private List<Integer> statuses = new ArrayList<>();

    /** 归一后的时间下界（含） */
    private LocalDateTime fromTime;

    /** 归一后的时间上界（含） */
    private LocalDateTime toTime;

    public void normalize() {
        if (pageNum == null || pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize == null || pageSize < 1) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isEmpty()) {
                keyword = null;
            } else if (keyword.length() > MAX_KEYWORD_LENGTH) {
                throw GoodsApiException.paramInvalid("搜索关键字最多 " + MAX_KEYWORD_LENGTH + " 字");
            }
        }
        stars = parseValues(star, "star", ALLOWED_STAR);
        statuses = parseValues(status, "status", ALLOWED_STATUS);
        fromTime = parseTime(dateFrom, "dateFrom");
        toTime = parseTime(dateTo, "dateTo");
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw GoodsApiException.paramInvalid("dateFrom 不能晚于 dateTo");
        }
    }

    /**
     * 解析逗号分隔的整数多值；空 ⇒ 空集合，非数值或不在白名单 ⇒ 400。
     *
     * @param raw     原始入参
     * @param field   字段名（用于错误提示）
     * @param allowed 白名单
     * @return 去重后的列表（保持入参顺序）
     */
    static List<Integer> parseValues(String raw, String field, List<Integer> allowed) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<Integer> values = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String text = part.trim();
            if (text.isEmpty()) {
                continue;
            }
            int value;
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw GoodsApiException.paramInvalid(field + " 取值非法：" + raw);
            }
            if (!allowed.contains(value)) {
                throw GoodsApiException.paramInvalid(field + " 取值非法：" + raw);
            }
            values.add(value);
        }
        return new ArrayList<>(values);
    }

    /** 时间入参：空 ⇒ null；格式错 ⇒ 400 {@code PARAM_INVALID} */
    static LocalDateTime parseTime(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), GoodsSupport.TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw GoodsApiException.paramInvalid(field + " 格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
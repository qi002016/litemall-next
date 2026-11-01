package com.litemall.domain.DTO;

import lombok.Data;

/**
 * <p>
 * 管理端品牌列表查询入参（08 v4 CN-25，goods 侧内部使用）。
 * </p>
 * <p>
 * admin 只做入参归一（页码/每页条数/关键字去空白/sortBy 白名单落默认）后经 Feign 透传，
 * 真正的筛选与排序在 goods 侧完成，因此本类只承载已归一的值。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
public class BrandAdminQuery {

    /** 页码（≥1，默认 1） */
    private Integer pageNum;

    /** 每页条数（1–100，默认 20） */
    private Integer pageSize;

    /** 品牌名模糊关键字（≤50），可空 */
    private String keyword;

    /** 排序字段：sortOrder | addTime | name（默认 sortOrder） */
    private String sortBy;

    /** 是否升序（默认 true） */
    private Boolean isAsc;
}
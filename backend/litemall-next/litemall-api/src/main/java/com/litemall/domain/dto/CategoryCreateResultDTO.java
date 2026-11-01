package com.litemall.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 分类新建出参（08 v4 CN-22：{@code {"id":"1036008","level":"2"}}，对内同形）。
 * </p>
 * <p>
 * {@code level} 取 {@code "1"}（{@code pid=0}）或 {@code "2"}，与
 * {@link CategoryAdminVO#getLevel()} 同一口径。
 * </p>
 *
 * @since 2026-11-01
 */
@Data
@Schema(description = "分类新建出参")
public class CategoryCreateResultDTO {

    @Schema(description = "新分类ID", type = "string")
    private Long id;

    @Schema(description = "层级：1=一级，2=二级")
    private String level;

    /**
     * 构造出参。
     *
     * @param id    新分类ID
     * @param level 层级（"1"/"2"）
     * @return 出参
     */
    public static CategoryCreateResultDTO of(Long id, String level) {
        CategoryCreateResultDTO dto = new CategoryCreateResultDTO();
        dto.setId(id);
        dto.setLevel(level);
        return dto;
    }
}
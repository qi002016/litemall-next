package com.litemall.domain.VO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * <p>
 * 文件上传结果VO
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Data
@Schema(description = "文件上传结果")
public class StorageVO {

    @Schema(description = "文件访问 URL")
    private String url;

    @Schema(description = "文件名")
    private String name;

    @Schema(description = "文件大小（字节）", type = "string")
    private Long size;
}

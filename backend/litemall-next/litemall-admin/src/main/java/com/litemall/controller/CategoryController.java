package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.config.OperationLogContext;
import com.litemall.domain.DTO.CategoryCreateDTO;
import com.litemall.domain.DTO.CategoryUpdateDTO;
import com.litemall.domain.Result;
import com.litemall.domain.dto.CategoryAdminDTO;
import com.litemall.domain.dto.CategoryAdminVO;
import com.litemall.domain.dto.CategoryCreateResultDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 管理端分类接口（R2-01：CN-21 / CN-22 / CN-23 / CN-24）。
 * </p>
 * <p>
 * 与商品接口同样的分层：admin 侧<b>不持有 Mapper</b>，只做「JSR-303 基础校验 + 转发 + 操作日志」，
 * 所有业务判定（父级是否存在、是否已是两级、同级重名、删除前的子分类与商品占用）在 goods 服务内完成
 * （05 §4.2）。因此经 admin 与经 {@code /inner} 直连的行为完全一致，错误码也一致：
 * </p>
 * <ul>
 *   <li>400 {@code PARAM_INVALID}：字段非法、三级分类、改父级；</li>
 *   <li>404 {@code RESOURCE_NOT_FOUND}：分类不存在；</li>
 *   <li>409 {@code CATEGORY_NAME_DUPLICATED} / {@code CATEGORY_HAS_CHILDREN} /
 *       {@code CATEGORY_IN_USE} / {@code CONCURRENT_MODIFIED}。</li>
 * </ul>
 * <p>
 * 操作日志：POST/PUT/DELETE 由 {@code OperationLogInterceptor} 统一落 {@code litemall_log}，
 * 本类只通过 {@link OperationLogContext#mark} 补 {@code comment/targetType/targetId}
 * （{@code target_type=goods}，05 §4.2）。
 * </p>
 *
 * @since 2026-11-01
 */
@RestController
@RequestMapping("/admin/category")
@RequiredArgsConstructor
@Tag(name = "管理端分类", description = "R2-01 分类树与分类 CRUD")
public class CategoryController {

    /** 操作日志对象类型（05 §4.2 冻结值） */
    private static final String LOG_TARGET_TYPE = "goods";

    private final GoodsClient goodsClient;

    /**
     * CN-21 分类树。
     *
     * @param withGoodsCount 是否返回每个分类下的商品数（含已下架，不含已删除），默认 false
     * @return 两级分类树
     */
    @Operation(summary = "管理端分类树")
    @GetMapping("/list")
    public Result<List<CategoryAdminVO>> list(
            @RequestParam(required = false) Boolean withGoodsCount) {
        return Result.ok(goodsClient.adminCategoryTree(Boolean.TRUE.equals(withGoodsCount)));
    }

    /**
     * CN-22 新建分类。
     *
     * @param body 分类信息（{@code pid} 缺省 0 即一级分类）
     * @return {@code {id, level}}
     */
    @Operation(summary = "新建分类")
    @PostMapping("/create")
    public Result<CategoryCreateResultDTO> create(@Valid @RequestBody CategoryCreateDTO body) {
        CategoryAdminDTO dto = new CategoryAdminDTO();
        dto.setPid(body.getPid());
        dto.setName(trimmed(body.getName()));
        dto.setPicUrl(trimmed(body.getPicUrl()));
        dto.setIconUrl(trimmed(body.getIconUrl()));
        dto.setSortOrder(body.getSortOrder());
        dto.setKeywords(trimmed(body.getKeywords()));
        dto.setDesc(body.getDesc());
        CategoryCreateResultDTO created = goodsClient.adminCreateCategory(dto);
        OperationLogContext.mark("新建分类：" + body.getName(), LOG_TARGET_TYPE,
                created == null ? null : created.getId());
        return Result.ok(created);
    }

    /**
     * CN-23 修改分类（部分更新；{@code pid} 冻结；{@code updateTime} 回传即 CAS）。
     *
     * @param body 分类信息（{@code id} 必填）
     * @return 空
     */
    @Operation(summary = "修改分类")
    @PutMapping("/update")
    public Result<Void> update(@Valid @RequestBody CategoryUpdateDTO body) {
        CategoryAdminDTO dto = new CategoryAdminDTO();
        dto.setId(body.getId());
        dto.setPid(body.getPid());
        dto.setName(trimmed(body.getName()));
        dto.setPicUrl(trimmed(body.getPicUrl()));
        dto.setIconUrl(trimmed(body.getIconUrl()));
        dto.setSortOrder(body.getSortOrder());
        dto.setKeywords(trimmed(body.getKeywords()));
        dto.setDesc(body.getDesc());
        dto.setUpdateTime(trimmed(body.getUpdateTime()));
        goodsClient.adminUpdateCategory(dto);
        OperationLogContext.mark("修改分类", LOG_TARGET_TYPE, body.getId());
        return Result.ok();
    }

    /**
     * CN-24 删除分类（有子分类 ⇒ 409 {@code CATEGORY_HAS_CHILDREN}；
     * 分类下仍有商品 ⇒ 409 {@code CATEGORY_IN_USE}「请先移走商品」；否则物理删除）。
     *
     * @param id 分类ID
     * @return 空
     */
    @Operation(summary = "删除分类")
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("id") Long id) {
        goodsClient.adminDeleteCategory(id);
        OperationLogContext.mark("删除分类", LOG_TARGET_TYPE, id);
        return Result.ok();
    }

    /** 部分更新时字段可缺省：null 原样透传，非空才去空白（空串⇒null 由 goods 侧按未传处理） */
    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PO.Category;
import com.litemall.domain.VO.CategoryVO;
import com.litemall.domain.dto.CategoryAdminDTO;
import com.litemall.domain.dto.CategoryAdminVO;
import com.litemall.domain.dto.CategoryCreateResultDTO;

import java.util.List;

/**
 * <p>
 * 商品分类表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
public interface ICategoryService extends IService<Category> {

    /**
     * 获取商品分类树(顶级分类 + 子分类)
     *
     * @return 分类树
     */
    List<CategoryVO> getCategoryTree();

    /* ==================== R2-01 管理端分类（CN-21~24） ==================== */

    /**
     * 管理端分类树（CN-21）：不分页、只含未删除节点、最多两级。
     *
     * @param withGoodsCount 是否返回每个分类下的商品数（含已下架，不含已删除）
     * @return 分类树（同层按 sort_order / id 升序，{@code children} 永不为 null）
     */
    List<CategoryAdminVO> adminTree(boolean withGoodsCount);

    /**
     * 新建分类（CN-22）。
     *
     * @param dto 分类信息（{@code pid} 缺省 0 表示一级分类）
     * @return {@code {id, level}}
     */
    CategoryCreateResultDTO createCategory(CategoryAdminDTO dto);

    /**
     * 修改分类（CN-23）：部分更新，{@code pid} 冻结，回传 {@code updateTime} 则做 CAS。
     *
     * @param dto 分类信息（{@code id} 必填）
     */
    void updateCategory(CategoryAdminDTO dto);

    /**
     * 删除分类（CN-24）：有子分类或有商品（含已下架）⇒ 409；否则<b>物理删除</b>
     * （05 §5.3 A 裁定：唯一键 {@code uk_category_name(pid,name)} 不含 {@code deleted}，
     * 软删会让同名分类永久不可重建）。
     *
     * @param id 分类ID
     */
    void deleteCategory(Long id);

    /* ==================== R2-02 C 端筛选（CI-31） ==================== */

    /**
     * C 端商品列表的分类过滤范围（08 §1.1 CI-31、05 §5.15 / R-15）。
     *
     * <p>
     * 传<b>一级</b>分类 ⇒ 展开为「自身 + 全部二级子分类」；传<b>末级</b>分类 ⇒ 仅自身；
     * 传不存在/已删除的分类 ⇒ <b>空列表</b>（调用方据此返回空页，<b>不报 404</b>）。
     * </p>
     *
     * @param categoryId 分类ID
     * @return 过滤用分类ID集合（永不为 {@code null}）
     */
    List<Long> categoryScopeIds(Long categoryId);
}
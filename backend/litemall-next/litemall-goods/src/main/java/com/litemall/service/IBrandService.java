package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PO.Brand;
import com.litemall.domain.DTO.BrandAdminQuery;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.BrandVO;
import com.litemall.domain.dto.BrandAdminDTO;
import com.litemall.domain.dto.BrandAdminVO;

/**
 * <p>
 * 品牌商表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
public interface IBrandService extends IService<Brand> {

    /**
     * 分页获取品牌列表（C 端 CI-34）。
     *
     * <p>
     * 分页参数可空：{@code null} 或 {@code < 1} 一律落契约默认值（{@code pageNum=1}、
     * {@code pageSize=20}），{@code pageSize} 上限 100——与 {@code docs/design/06} 分页口径一致，
     * 避免不传参时在参数绑定层直接 500。
     * </p>
     *
     * @param pageNum  页码，可空（缺省 1）
     * @param pageSize 每页条数，可空（缺省 20，上限 100）
     * @return 品牌分页（{@code floorPrice} 为整数分）
     */
    PageVO<BrandVO> getBrandList(Integer pageNum, Integer pageSize);

    /* ==================== R2-01 管理端品牌（CN-25~28） ==================== */

    /**
     * 管理端品牌分页（CN-25）：支持关键字模糊、排序白名单、商品数统计。
     *
     * @param query 已归一化查询条件（可空，全落默认值）
     * @return 品牌分页
     */
    PageVO<BrandAdminVO> adminListBrands(BrandAdminQuery query);

    /**
     * 新建品牌（CN-26）。
     *
     * @param dto 品牌信息
     * @return 新品牌ID
     */
    Long createBrand(BrandAdminDTO dto);

    /**
     * 修改品牌（CN-27）：部分更新，回传 {@code updateTime} 则做 CAS。
     *
     * @param dto 品牌信息（{@code id} 必填）
     */
    void updateBrand(BrandAdminDTO dto);

    /**
     * 删除品牌（CN-28）：仍有商品（含已下架）⇒ 409 {@code BRAND_IN_USE}；
     * 否则<b>物理删除</b>（05 §5.3 A 裁定，唯一键 {@code uk_brand_name} 不含 {@code deleted}）。
     *
     * @param id 品牌ID
     */
    void deleteBrand(Long id);
}
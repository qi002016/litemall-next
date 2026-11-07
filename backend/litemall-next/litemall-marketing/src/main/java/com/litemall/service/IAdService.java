package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PO.Ad;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.AdVO;
import com.litemall.domain.dto.AdDTO;

/**
 * <p>
 * 广告表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-23
 */
public interface IAdService extends IService<Ad> {

    /**
     * C 端首页轮播：只认 position=home_banner（内部映射成库里的 tinyint 1），
     * 返回 PageVO 而非裸 List（D2b），limit 默认 5、最大 5（BR-CMS-03）。
     */
    PageVO<AdVO> getAdList(String position, Integer limit);

    /** 管理端分页列表，排序规则与 C 端一致：sortOrder ASC, id DESC */
    PageVO<AdDTO> adminListAds(Integer pageNum, Integer pageSize, String name);

    /** 管理端新建：落 linkType/sortOrder，提交后失效广告缓存 */
    Long createAd(AdDTO dto);

    /** 管理端更新：落 linkType/sortOrder，提交后失效广告缓存 */
    void updateAd(AdDTO dto);

    /** 管理端逻辑删除，提交后失效广告缓存 */
    void deleteAd(Long id);
}

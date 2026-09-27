package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.DTO.CollectAddDTO;
import com.litemall.domain.PO.Collect;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CollectVO;

/**
 * <p>
 * 收藏表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
public interface ICollectService extends IService<Collect> {

    /**
     * 我的收藏列表
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页收藏列表
     */
    PageVO<CollectVO> listCollect(Integer pageNum, Integer pageSize);

    /**
     * 收藏/取消收藏
     *
     * @param dto 收藏参数
     * @return 当前是否已收藏
     */
    boolean addCollect(CollectAddDTO dto);
}

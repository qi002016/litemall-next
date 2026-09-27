package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.DTO.AftersaleApplyDTO;
import com.litemall.domain.PO.Aftersale;
import com.litemall.domain.VO.AftersaleApplyVO;

/**
 * <p>
 * 售后表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
public interface IAftersaleService extends IService<Aftersale> {

    /**
     * 申请售后
     *
     * @param dto 售后申请参数
     * @return 售后申请结果
     */
    AftersaleApplyVO applyAftersale(AftersaleApplyDTO dto);
}

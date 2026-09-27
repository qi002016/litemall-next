package com.litemall.service;


import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PageVO;
import com.litemall.domain.PO.GrouponRule;
import com.litemall.domain.VO.GrouponRuleVO;

/**
 * <p>
 * 团购规则表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-24
 */
public interface IGrouponRuleService extends IService<GrouponRule> {

    /**
     * 团购活动列表（正常上线且未过期）
     */
    PageVO<GrouponRuleVO> getGrouponList(Integer pageNum, Integer pageSize);

}

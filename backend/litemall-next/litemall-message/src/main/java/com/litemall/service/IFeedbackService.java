package com.litemall.service;

import com.litemall.domain.DTO.FeedbackSubmitDTO;
import com.litemall.domain.VO.FeedbackVO;

/**
 * <p>
 * 意见反馈服务
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
public interface IFeedbackService {

    /**
     * 提交意见反馈
     *
     * @param dto 反馈内容
     * @return 反馈记录ID
     */
    FeedbackVO submit(FeedbackSubmitDTO dto);
}

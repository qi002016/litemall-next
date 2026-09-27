package com.litemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litemall.domain.PO.Feedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 意见反馈表 Mapper
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {
}

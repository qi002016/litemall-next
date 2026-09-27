package com.litemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litemall.domain.PO.UserMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 用户消息表 Mapper
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {
}

package com.litemall.mapper;

import com.litemall.domain.PO.Comment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 评论表 Mapper 接口
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

}

package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.DTO.CommentDTO;
import com.litemall.domain.PO.Comment;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CommentVO;

/**
 * <p>
 * 评论表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
public interface ICommentService extends IService<Comment> {

    /**
     * 分页获取商品评论列表
     *
     * @param goodsId  商品ID
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 评论分页
     */
    PageVO<CommentVO> getCommentList(Long goodsId, int pageNum, int pageSize);

    /**
     * 发表商品评论
     *
     * @param dto 评论内容
     */
    void addComment(CommentDTO dto);
}

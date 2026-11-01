package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.DTO.CommentAdminQuery;
import com.litemall.domain.DTO.CommentDTO;
import com.litemall.domain.PO.Comment;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CommentPostResultVO;
import com.litemall.domain.VO.CommentVO;
import com.litemall.domain.dto.CommentAdminVO;
import com.litemall.domain.dto.CommentReplyDTO;
import com.litemall.domain.dto.CommentReplyResultDTO;
import com.litemall.domain.dto.CommentStatusDTO;
import com.litemall.domain.dto.CommentStatusResultDTO;

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
     * 商品评价列表（CI-35）：只返回 {@code status=0}（显示中），total 也只计未隐藏。
     *
     * @param goodsId    商品ID
     * @param star       星级筛选（1–5），可空
     * @param hasPicture 是否只看带图，可空
     * @param pageNum    页码（≥1）
     * @param pageSize   每页条数（1–100）
     * @return 评价分页
     */
    PageVO<CommentVO> getCommentList(Long goodsId, Integer star, Boolean hasPicture, int pageNum, int pageSize);

    /**
     * 发表评价（CI-36）：按 05 §2.3.3 九步校验链执行，落库提交后再同步订单完成态。
     *
     * @param dto 评价入参
     * @return 新评价ID 与订单状态同步结果
     */
    CommentPostResultVO addComment(CommentDTO dto);

    /**
     * 管理端评价分页（CN-29）：<b>含已隐藏</b>，与 C 端口径不同。
     *
     * @param query 查询条件（已归一）
     * @return 分页评价行
     */
    PageVO<CommentAdminVO> adminListComments(CommentAdminQuery query);

    /**
     * 管理端回复评价（CN-30）：覆盖写，{@code reply_time} 每次刷新。
     *
     * @param dto 回复入参
     * @return 回复结果
     */
    CommentReplyResultDTO replyComment(CommentReplyDTO dto);

    /**
     * 管理端隐藏/恢复评价（CN-31）：{@code status} 是目标态，CAS 落空但已是目标态时幂等成功。
     *
     * @param dto 状态入参
     * @return 状态结果
     */
    CommentStatusResultDTO updateCommentStatus(CommentStatusDTO dto);
}
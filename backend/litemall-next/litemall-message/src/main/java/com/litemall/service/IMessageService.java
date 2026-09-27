package com.litemall.service;

import com.litemall.domain.PageVO;
import com.litemall.domain.VO.MessageDetailVO;
import com.litemall.domain.VO.MessageVO;

/**
 * <p>
 * 用户消息服务
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
public interface IMessageService {

    /**
     * 我的消息列表
     *
     * @param type     消息类型（order/system/coupon，可选）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页消息列表
     */
    PageVO<MessageVO> listMessage(String type, Integer pageNum, Integer pageSize);

    /**
     * 消息详情
     *
     * @param id 用户消息记录ID
     * @return 消息详情；不存在返回 null
     */
    MessageDetailVO getMessageDetail(Long id);

    /**
     * 标记已读
     *
     * @param id 用户消息记录ID
     */
    void markRead(Long id);

    /**
     * 推送消息（服务间内部接口，供 order/marketing 等服务调用）
     *
     * @param userId  接收用户ID
     * @param type    消息类型
     * @param title   标题
     * @param content 内容
     */
    void push(Long userId, String type, String title, String content);
}

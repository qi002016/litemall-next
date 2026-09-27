package com.litemall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.PO.UserMessage;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.MessageDetailVO;
import com.litemall.domain.VO.MessageVO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ForbiddenException;
import com.litemall.mapper.UserMessageMapper;
import com.litemall.service.IMessageService;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户消息服务 实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<UserMessageMapper, UserMessage> implements IMessageService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int BRIEF_LENGTH = 50;

    @Override
    public PageVO<MessageVO> listMessage(String type, Integer pageNum, Integer pageSize) {
        Long userId = UserContext.getUser();
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Page<UserMessage> page = new Page<>(pn, ps);
        Page<UserMessage> messagePage = lambdaQuery()
                .eq(UserMessage::getUserId, userId.intValue())
                .eq(UserMessage::getDeleted, false)
                .eq(StrUtil.isNotBlank(type), UserMessage::getType, type)
                .orderByDesc(UserMessage::getAddTime)
                .page(page);

        List<MessageVO> voList = messagePage.getRecords().stream().map(m -> {
            MessageVO vo = new MessageVO();
            vo.setId(m.getId().longValue());
            vo.setMsgId(m.getMsgId() == null ? 0L : m.getMsgId().longValue());
            vo.setType(m.getType());
            vo.setTitle(m.getTitle());
            vo.setBrief(StrUtil.isBlank(m.getContent()) ? ""
                    : (m.getContent().length() <= BRIEF_LENGTH ? m.getContent() : m.getContent().substring(0, BRIEF_LENGTH)));
            vo.setReadStatus(Boolean.TRUE.equals(m.getReadStatus()));
            vo.setAddTime(m.getAddTime() == null ? null : m.getAddTime().format(DT));
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(messagePage.getTotal(), messagePage.getPages(), voList);
    }

    @Override
    public MessageDetailVO getMessageDetail(Long id) {
        UserMessage message = requireOwnMessage(id);
        MessageDetailVO vo = new MessageDetailVO();
        vo.setId(message.getId().longValue());
        vo.setType(message.getType());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setReadStatus(Boolean.TRUE.equals(message.getReadStatus()));
        vo.setAddTime(message.getAddTime() == null ? null : message.getAddTime().format(DT));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id) {
        UserMessage message = requireOwnMessage(id);
        if (!Boolean.TRUE.equals(message.getReadStatus())) {
            message.setReadStatus(true);
            message.setUpdateTime(LocalDateTime.now());
            updateById(message);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void push(Long userId, String type, String title, String content) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("接收用户不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        UserMessage message = new UserMessage()
                .setUserId(userId.intValue())
                .setMsgId(0)
                .setType(StrUtil.isBlank(type) ? "system" : type)
                .setTitle(title == null ? "" : title)
                .setContent(content == null ? "" : content)
                .setReadStatus(false)
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        save(message);
    }

    /**
     * 校验消息存在且属于当前登录用户
     */
    private UserMessage requireOwnMessage(Long id) {
        if (id == null) {
            throw new BadRequestException("消息ID不能为空");
        }
        Long userId = UserContext.getUser();
        UserMessage message = getById(id.intValue());
        if (message == null || Boolean.TRUE.equals(message.getDeleted())) {
            throw new BadRequestException("消息不存在");
        }
        if (userId != null && !userId.equals(message.getUserId().longValue())) {
            throw new ForbiddenException("无权操作该消息");
        }
        return message;
    }
}

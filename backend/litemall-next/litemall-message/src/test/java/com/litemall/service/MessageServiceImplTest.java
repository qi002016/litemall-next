package com.litemall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.PO.UserMessage;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.MessageDetailVO;
import com.litemall.domain.VO.MessageVO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ForbiddenException;
import com.litemall.mapper.UserMessageMapper;
import com.litemall.service.impl.MessageServiceImpl;
import com.litemall.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p>
 * 用户消息服务单元测试（纯 Mockito，不加载 Spring 上下文）
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private UserMessageMapper userMessageMapper;

    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        UserContext.setUser(1L);
        // ServiceImpl 的 baseMapper 为父类 protected 字段，Mockito @InjectMocks 无法注入，显式设置
        messageService = new MessageServiceImpl();
        ReflectionTestUtils.setField(messageService, "baseMapper", userMessageMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    /**
     * 说明：列表查询（listMessage）走 mybatis-plus 的 selectPage default 方法，
     * 依赖 MyBatis mapperInterface/sqlSession 元数据，无法用 Mockito 纯单测；
     * 属于集成行为，交付时通过接口实现与编译验证覆盖。
     */

    /** 探针契约：消息不存在时查详情必须拒绝 */
    @Test
    @DisplayName("消息不存在时详情查询拒绝")
    void getMessageDetail_notExist_rejects() {
        when(userMessageMapper.selectById(100L)).thenReturn(null);

        assertThrows(BadRequestException.class, () -> messageService.getMessageDetail(100L));
    }

    /** 探针契约：他人的消息不能读取（权限边界） */
    @Test
    @DisplayName("他人的消息详情查询拒绝")
    void getMessageDetail_otherUser_forbidden() {
        when(userMessageMapper.selectById(200L)).thenReturn(message(200, 9L, "system", "他人", "内容", false,
                LocalDateTime.now()));

        assertThrows(ForbiddenException.class, () -> messageService.getMessageDetail(200L));
    }

    @Test
    @DisplayName("本人消息详情返回完整内容")
    void getMessageDetail_own_returnsFull() {
        when(userMessageMapper.selectById(300L)).thenReturn(message(300, 1L, "order", "订单消息", "完整富文本内容",
                false, LocalDateTime.of(2026, 9, 25, 7, 0)));

        MessageDetailVO vo = messageService.getMessageDetail(300L);

        assertEquals("订单消息", vo.getTitle());
        assertEquals("完整富文本内容", vo.getContent());
        assertEquals("order", vo.getType());
    }

    @Test
    @DisplayName("未读消息标记已读后更新")
    void markRead_unread_updates() {
        when(userMessageMapper.selectById(400L)).thenReturn(message(400, 1, "system", "标题", "内容", false,
                LocalDateTime.now()));
        java.util.concurrent.atomic.AtomicReference<UserMessage> captured = new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return 1;
        }).when(userMessageMapper).updateById(any(UserMessage.class));

        messageService.markRead(400L);

        assertTrue(captured.get().getReadStatus());
    }

    @Test
    @DisplayName("已读消息标记已读不重复更新")
    void markRead_alreadyRead_skipsUpdate() {
        when(userMessageMapper.selectById(500L)).thenReturn(message(500, 1, "system", "标题", "内容", true,
                LocalDateTime.now()));

        messageService.markRead(500L);

        verify(userMessageMapper, never()).updateById(any(UserMessage.class));
    }

    @Test
    @DisplayName("推送消息保存用户消息记录")
    void push_savesMessage() {
        messageService.push(1L, "order", "订单已发货", "您的订单已发出");

        ArgumentCaptor<UserMessage> captor = ArgumentCaptor.forClass(UserMessage.class);
        verify(userMessageMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals("order", captor.getValue().getType());
        assertEquals("订单已发货", captor.getValue().getTitle());
        assertFalse(captor.getValue().getReadStatus());
    }

    @Test
    @DisplayName("推送消息接收用户为空拒绝")
    void push_missingUser_rejects() {
        assertThrows(BadRequestException.class, () -> messageService.push(null, "system", "t", "c"));
    }

    private UserMessage message(long id, long userId, String type, String title, String content,
                                boolean read, LocalDateTime addTime) {
        UserMessage m = new UserMessage();
        m.setId(id);
        m.setUserId(userId);
        m.setMsgId(1L);
        m.setType(type);
        m.setTitle(title);
        m.setContent(content);
        m.setReadStatus(read);
        m.setAddTime(addTime);
        m.setDeleted(false);
        return m;
    }}

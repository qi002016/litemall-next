package com.litemall.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R4-MQ：延时消息投递的时机、失败隔离与延时等级换算。
 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutPublisherTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private ObjectProvider<RocketMQTemplate> provider;

    private OrderTimeoutPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderTimeoutPublisher(provider);
        ReflectionTestUtils.setField(publisher, "enabled", true);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("无事务：立即投递，30 分钟 → 延时等级 16")
    void publishWithoutTransaction_sendsImmediately() {
        when(provider.getIfAvailable()).thenReturn(rocketMQTemplate);

        publisher.publishAfterCommit(1L, LocalDateTime.now().plusMinutes(30));

        verify(rocketMQTemplate).syncSend(eq(OrderTimeoutPublisher.TOPIC), any(Message.class),
                eq(3000L), eq(16));
    }

    @Test
    @DisplayName("有事务：提交前不投递，afterCommit 才投递一次")
    void publishInTransaction_sendsAfterCommitOnly() {
        when(provider.getIfAvailable()).thenReturn(rocketMQTemplate);
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit(2L, LocalDateTime.now().plusMinutes(30));
        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class), anyLong(), anyInt());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(rocketMQTemplate, times(1)).syncSend(anyString(), any(Message.class), anyLong(), anyInt());
    }

    @Test
    @DisplayName("有事务但回滚：不投递")
    void publishInTransaction_rollback_noSend() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publishAfterCommit(3L, LocalDateTime.now().plusMinutes(30));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class), anyLong(), anyInt());
    }

    @Test
    @DisplayName("broker 不可用：异常被吞掉，不影响下单")
    void publishSendFailure_swallowed() {
        when(provider.getIfAvailable()).thenReturn(rocketMQTemplate);
        doThrow(new RuntimeException("broker down")).when(rocketMQTemplate)
                .syncSend(anyString(), any(Message.class), anyLong(), anyInt());

        publisher.publishAfterCommit(4L, LocalDateTime.now().plusMinutes(30));

        verify(rocketMQTemplate).syncSend(anyString(), any(Message.class), anyLong(), anyInt());
    }

    @Test
    @DisplayName("开关关闭：不取模板、不投递")
    void publishDisabled_noSend() {
        ReflectionTestUtils.setField(publisher, "enabled", false);

        publisher.publishAfterCommit(5L, LocalDateTime.now().plusMinutes(30));

        verify(provider, never()).getIfAvailable();
        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class), anyLong(), anyInt());
    }

    @Test
    @DisplayName("入参不全：静默跳过")
    void publishNullArgs_noSend() {
        publisher.publishAfterCommit(null, LocalDateTime.now().plusMinutes(30));
        publisher.publishAfterCommit(6L, null);

        verify(provider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("延时等级换算：向上取整到最接近的等级，超上限用最高级")
    void delayLevel_mapping() {
        assertThat(OrderTimeoutPublisher.delayLevelOf(0)).isEqualTo(1);
        assertThat(OrderTimeoutPublisher.delayLevelOf(1)).isEqualTo(1);
        assertThat(OrderTimeoutPublisher.delayLevelOf(2)).isEqualTo(2);
        assertThat(OrderTimeoutPublisher.delayLevelOf(60)).isEqualTo(5);
        assertThat(OrderTimeoutPublisher.delayLevelOf(61)).isEqualTo(6);
        assertThat(OrderTimeoutPublisher.delayLevelOf(1800)).isEqualTo(16);
        assertThat(OrderTimeoutPublisher.delayLevelOf(99999)).isEqualTo(18);
    }
}
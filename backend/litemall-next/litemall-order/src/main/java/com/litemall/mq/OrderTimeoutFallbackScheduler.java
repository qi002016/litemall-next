package com.litemall.mq;

import com.litemall.domain.dto.CloseExpiredResultDTO;
import com.litemall.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时关单兜底扫描（R4-MQ 的降级通道）。
 *
 * <p>与 MQ 延时消息并存，覆盖三种 MQ 覆盖不到的情况：① 消息投递失败；② broker 长时间不可用；
 * ③ 消息进了死信队列没人管。多实例部署时各实例都会扫，但关单本身是逐单 CAS，
 * 重复扫描只会得到 {@code affected=0}，不会重复释放资源。</p>
 *
 * <p>可用 {@code hm.order.timeout-scan.enabled=false} 关停（例如交给外部调度器统一打 CN-11 接口）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hm.order.timeout-scan.enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutFallbackScheduler {

    /** 单批上限，与 CN-11 的 1–200 约束一致。 */
    private static final int SCAN_LIMIT = 200;

    private final IOrderService orderService;

    @Scheduled(initialDelayString = "${hm.order.timeout-scan.initial-ms:60000}",
            fixedDelayString = "${hm.order.timeout-scan.ms:300000}")
    public void scanExpiredOrders() {
        try {
            CloseExpiredResultDTO result = orderService.adminCloseExpired(SCAN_LIMIT, false);
            if (result.getSucceeded() > 0 || result.getFailed() > 0) {
                log.info("[兜底扫描] 超时关单：total={}, succeeded={}, failed={}",
                        result.getTotal(), result.getSucceeded(), result.getFailed());
            }
        } catch (Exception e) {
            log.error("[兜底扫描] 超时关单执行失败，下一周期重试", e);
        }
    }
}
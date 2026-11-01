package com.litemall.support;

import com.litemall.client.OrderClient;
import com.litemall.domain.dto.LockedStockDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 锁定库存（locked）取数器：goods 侧唯一的 order 依赖点。
 *
 * <p>
 * 08 §2.1 冻结口径：{@code number} 是唯一持久化库存，{@code locked} 由 order 域 CN-IN-04 实时派生
 * （{@code order_status=101} 且订单行未删除，超时未关单仍计入），{@code sellable = max(0, number − locked)}。
 * 因此商品域**不落** {@code locked_number} 列，只在读路径按需聚合。
 * </p>
 *
 * <p>
 * 降级策略（03 §4.3 / 08 §2.1）：order 不可达或返回异常时，{@code available=false}，
 * 调用方必须把 {@code locked}/{@code sellable} 置为 {@code null} 并标记 {@code lockedUnavailable}，
 * <b>不得</b>把「未知」当成 0（否则会虚报可售，引发超卖）。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LockedStockProvider {

    /** CN-IN-04 单次查询上限（productIds 逗号分隔 ≤200） */
    private static final int MAX_IDS_PER_CALL = 200;

    private final OrderClient orderClient;

    /**
     * 查询一批货品的锁定数量。
     *
     * @param productIds 货品ID集合（可为空；为空时直接返回空结果，避免无谓 RPC）
     * @return 查询结果（含可用性标记）
     */
    public LockedStockQuery query(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return LockedStockQuery.available(Map.of());
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : productIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return LockedStockQuery.available(Map.of());
        }
        Map<Long, Integer> locked = new HashMap<>();
        List<Long> batch = new ArrayList<>(MAX_IDS_PER_CALL);
        try {
            for (Long id : distinct) {
                batch.add(id);
                if (batch.size() == MAX_IDS_PER_CALL) {
                    merge(locked, batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                merge(locked, batch);
            }
            return LockedStockQuery.available(locked);
        } catch (Exception e) {
            // order 不可达/接口未就绪：明确降级，不吞成 0
            log.warn("查询锁定库存失败，locked/sellable 降级为未知：{}", e.getMessage());
            return LockedStockQuery.unavailable();
        }
    }

    private void merge(Map<Long, Integer> target, List<Long> ids) {
        String productIds = String.join(",", ids.stream().map(String::valueOf).toList());
        LockedStockDTO dto = orderClient.lockedStock(productIds);
        if (dto == null || dto.getList() == null) {
            return;
        }
        for (LockedStockDTO.Item item : dto.getList()) {
            if (item == null || item.getProductId() == null) {
                continue;
            }
            target.put(item.getProductId(), item.getLockedQty() == null ? 0 : item.getLockedQty());
        }
    }

    /**
     * 锁定库存查询结果。
     *
     * @param available order 侧是否可用
     * @param locked    productId → lockedQty（available=false 时为空 Map）
     */
    public record LockedStockQuery(boolean available, Map<Long, Integer> locked) {

        public static LockedStockQuery available(Map<Long, Integer> locked) {
            return new LockedStockQuery(true, locked == null ? Map.of() : locked);
        }

        public static LockedStockQuery unavailable() {
            return new LockedStockQuery(false, Map.of());
        }

        /** 可用时返回占用数（无占用⇒0）；不可用时返回 {@code null}（未知，调用方不得当 0） */
        public Integer lockedOf(Long productId) {
            if (!available) {
                return null;
            }
            Integer value = locked.get(productId);
            return value == null ? 0 : value;
        }
    }
}
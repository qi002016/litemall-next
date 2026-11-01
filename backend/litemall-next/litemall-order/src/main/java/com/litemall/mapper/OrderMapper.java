package com.litemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litemall.domain.PO.Order;
import com.litemall.domain.dto.LockedStockDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * <p>
 * 订单表 Mapper
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * R1（CN-IN-04）：按 SKU 聚合「锁定量」——{@code order_status=101 AND og.deleted=0} 的 number 之和。
     * <p>
     * 口径（07 §6.7 冻结）：
     * <ul>
     *   <li>只认 {@code order_status=101}：102/201/301/501/103 都不计入（已支付即转实扣）；</li>
     *   <li><b>超时未关单仍计入</b>：不带任何时间条件、不看 {@code end_time}/{@code expire_time}；</li>
     *   <li>排除逻辑删除的行（订单本身与订单行都要 {@code deleted=0}）；</li>
     *   <li>库内一次 {@code GROUP BY} 聚合，禁 N+1（goods 每次算可售都会调）。</li>
     * </ul>
     * productIds 为空表示「全部有占用的 SKU」。
     * </p>
     */
    @Select("""
            <script>
            SELECT og.product_id AS productId, SUM(og.number) AS lockedQty
            FROM litemall_order o
            JOIN litemall_order_goods og ON og.order_id = o.id AND og.deleted = 0
            WHERE o.deleted = 0 AND o.order_status = 101
            <if test="productIds != null and productIds.size() > 0">
              AND og.product_id IN
              <foreach collection="productIds" item="pid" open="(" separator="," close=")">#{pid}</foreach>
            </if>
            GROUP BY og.product_id
            HAVING SUM(og.number) &gt; 0
            ORDER BY og.product_id ASC
            </script>
            """)
    List<LockedStockDTO.Item> selectLockedStock(@Param("productIds") List<Long> productIds);

    /**
     * R2 CN-IN-08 ②：301 → 501 的唯一写点（R2-00 §4.4 冻结 SQL-2）。
     * <p>
     * 三条件全部落在 WHERE 里（单条原子 CAS，禁止「读-判-写」）：
     * <ol>
     *   <li>当前 {@code order_status=301}（已 501 / 未收货 / 已关闭都不会命中）；</li>
     *   <li>{@code EXISTS} 至少一行未全退（{@code refund_status<>2}）——不得因「所有行已退款」跃迁；</li>
     *   <li>{@code NOT EXISTS} 任一「未退款且未评价」行——已评价即 {@code comment>0}；
     *       {@code comment=-1}（超期不可评）不参与判定，保持 -1 不动。</li>
     * </ol>
     * <b>禁止</b>写 {@code end_time}/{@code close_type}（关闭/取消语义专用）；<b>禁止</b>定时/批量调用。
     * </p>
     *
     * @return 受影响行数（1=本次跃迁成功，0=条件不满足/已被并发跃迁 ⇒ 幂等 changed=false）
     */
    @Update("""
            UPDATE litemall_order o
               SET o.order_status = 501, o.update_time = NOW()
             WHERE o.id = #{orderId}
               AND o.deleted = 0
               AND o.order_status = 301
               AND EXISTS (SELECT 1 FROM litemall_order_goods og
                            WHERE og.order_id = o.id AND og.deleted = 0 AND og.refund_status <> 2)
               AND NOT EXISTS (SELECT 1 FROM litemall_order_goods og
                                WHERE og.order_id = o.id AND og.deleted = 0
                                  AND og.refund_status = 0
                                  AND og.comment = 0)
            """)
    int casComplete301To501(@Param("orderId") Long orderId);
}
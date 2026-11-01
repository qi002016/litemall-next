package com.litemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litemall.domain.PO.Order;
import com.litemall.domain.dto.LockedStockDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
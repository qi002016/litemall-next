package com.litemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litemall.domain.PO.OrderGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 订单商品表 Mapper
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Mapper
public interface OrderGoodsMapper extends BaseMapper<OrderGoods> {

    /**
     * R2 CN-IN-08 ①：幂等标记订单行已评价（{@code comment=0} 才写，重复调用 affected=0）。
     * <p>
     * 条件里的 {@code order_id} 是「行必须属于该订单」的硬约束；不属于该订单时 affected=0，
     * 服务层在调用前已用 select 做 404 判定，这里只做幂等闸门。
     * </p>
     *
     * @return 受影响行数（1=本次标记成功，0=已标记过 / 行不匹配）
     */
    @Update("""
            UPDATE litemall_order_goods
               SET comment = #{commentId}, update_time = NOW()
             WHERE id = #{orderGoodsId}
               AND order_id = #{orderId}
               AND deleted = 0
               AND comment = 0
            """)
    int markCommented(@Param("orderGoodsId") Long orderGoodsId,
                      @Param("orderId") Long orderId,
                      @Param("commentId") Long commentId);
}

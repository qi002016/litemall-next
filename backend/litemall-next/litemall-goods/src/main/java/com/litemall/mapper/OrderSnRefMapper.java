package com.litemall.mapper;

import com.litemall.domain.DTO.OrderSnDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 订单号只读查询（跨域只读，05 §1.7 允许且已有先例 {@link OrderGoodsRefMapper}）。
 * </p>
 * <p>
 * 用途仅限管理端评价列表展示 {@code orderSn}（05 §3.9）：评价行只有 {@code order_id}，
 * 而订单号在 order 域自有表 {@code litemall_order}。本 Mapper <b>只读</b>、无写、不 Join，
 * 与 order 域「order_goods.comment / order.status 只能由订单服务写」的红线不冲突。
 * </p>
 *
 * @since 2026-11-01
 */
@Mapper
public interface OrderSnRefMapper {

    /**
     * 批量取订单号。
     *
     * @param ids 订单ID集合（调用方保证非空）
     * @return 每行 {@code {id, orderSn}}；订单不存在时不返回该行
     */
    @Select("<script>"
            + "SELECT id AS id, order_sn AS orderSn FROM litemall_order WHERE id IN "
            + "<foreach collection='ids' item='item' open='(' separator=',' close=')'>#{item}</foreach>"
            + "</script>")
    List<OrderSnDTO> selectOrderSns(@Param("ids") Collection<Long> ids);
}
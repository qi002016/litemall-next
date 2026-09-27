package com.litemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litemall.domain.PO.OrderGoods;
import org.apache.ibatis.annotations.Mapper;

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
}

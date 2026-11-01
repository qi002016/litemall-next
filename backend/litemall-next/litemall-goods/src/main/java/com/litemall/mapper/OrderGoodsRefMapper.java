package com.litemall.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 订单行引用计数（只读跨表查询）。
 * </p>
 *
 * <p>
 * 用途仅限删除/规格变更前的引用判断（08 v4 CI-12 GOODS_IN_USE、CI-11 SKU_IN_USE）：
 * 商品/SKU 一旦被订单行引用就不允许物理语义的删除，只能改为下架或把库存置 0。
 * 这里只读 {@code litemall_order_goods}，不写、不 Join 其它表，goods 域仍然只拥有自己的表。
 * </p>
 *
 * <p>
 * 注意：不做 {@code deleted=0} 过滤——逻辑删除的订单行同样是历史引用，
 * 删除商品后历史订单就无法再展示商品名，故按"存在任一订单行"判断。
 * </p>
 */
@Mapper
public interface OrderGoodsRefMapper {

    /**
     * 商品被订单行引用的数量。
     *
     * @param goodsId 商品ID
     * @return 引用行数
     */
    @Select("SELECT COUNT(1) FROM litemall_order_goods WHERE goods_id = #{goodsId}")
    long countByGoodsId(@Param("goodsId") Long goodsId);

    /**
     * 货品（SKU）被订单行引用的数量。
     *
     * @param productId 货品ID
     * @return 引用行数
     */
    @Select("SELECT COUNT(1) FROM litemall_order_goods WHERE product_id = #{productId}")
    long countByProductId(@Param("productId") Long productId);
}
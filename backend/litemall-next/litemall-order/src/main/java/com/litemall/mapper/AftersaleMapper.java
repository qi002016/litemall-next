package com.litemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.litemall.domain.DTO.RefundBriefDTO;
import com.litemall.domain.PO.Aftersale;
import com.litemall.domain.dto.AftersaleAdminDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 售后表 Mapper。
 * </p>
 * <p>
 * 除 MyBatis-Plus 基础能力外，只增加两条只读查询：
 * <ul>
 *   <li>{@link #selectAdminPage}：CN-01 管理端列表需要联表 {@code litemall_order}（orderSn/收货人/手机号）
 *       与 {@code litemall_order_goods}（商品名/图片/规格）做筛选与展示，否则关键字分页会失真；</li>
 *   <li>{@link #selectRefundBrief}：售后表无 refund_id 列（35 列不变），退款单号与退款时间从
 *       {@code litemall_refund} 只读取得。</li>
 * </ul>
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Mapper
public interface AftersaleMapper extends BaseMapper<Aftersale> {

    /**
     * CN-01 管理端售后分页（状态/类型/单号/关键字/申请时间），金额转整数分，时间为字符串。
     */
    @Select("""
            <script>
            SELECT a.id AS aftersaleId,
                   a.aftersale_sn AS aftersaleSn,
                   a.order_id AS orderId,
                   o.order_sn AS orderSn,
                   a.order_goods_id AS orderGoodsId,
                   a.user_id AS userId,
                   og.goods_name AS goodsName,
                   og.pic_url AS picUrl,
                   og.specifications AS specText,
                   a.number AS number,
                   a.type AS type,
                   a.status AS status,
                   CAST(ROUND(a.amount * 100) AS SIGNED) AS amount,
                   a.restock_status AS restockStatus,
                   a.return_ship_sn AS returnShipSn,
                   DATE_FORMAT(a.return_deadline, '%Y-%m-%d %H:%i:%s') AS returnDeadline,
                   DATE_FORMAT(a.add_time, '%Y-%m-%d %H:%i:%s') AS applyTime,
                   o.consignee AS consignee,
                   a.audit_admin AS auditAdmin
              FROM litemall_aftersale a
              LEFT JOIN litemall_order o ON o.id = a.order_id AND o.deleted = 0
              LEFT JOIN litemall_order_goods og ON og.id = a.order_goods_id AND og.deleted = 0
             WHERE a.deleted = 0
             <if test="statuses != null and statuses.size() > 0">
               AND a.status IN
               <foreach collection="statuses" item="st" open="(" separator="," close=")">#{st}</foreach>
             </if>
             <if test="type != null"> AND a.type = #{type} </if>
             <if test="aftersaleSn != null and aftersaleSn != ''">
               AND a.aftersale_sn LIKE CONCAT(#{aftersaleSn}, '%')
             </if>
             <if test="orderSn != null and orderSn != ''">
               AND o.order_sn LIKE CONCAT(#{orderSn}, '%')
             </if>
             <if test="keyword != null and keyword != ''">
               AND (o.consignee LIKE CONCAT('%', #{keyword}, '%')
                    OR o.mobile LIKE CONCAT('%', #{keyword}, '%')
                    OR og.goods_name LIKE CONCAT('%', #{keyword}, '%'))
             </if>
             <if test="applyTimeMin != null and applyTimeMin != ''"> AND a.add_time &gt;= #{applyTimeMin} </if>
             <if test="applyTimeMax != null and applyTimeMax != ''"> AND a.add_time &lt;= #{applyTimeMax} </if>
             ORDER BY ${orderBy} ${direction}, a.id ${direction}
            </script>
            """)
    List<AftersaleAdminDTO> selectAdminPage(IPage<AftersaleAdminDTO> page,
                                            @Param("statuses") List<Integer> statuses,
                                            @Param("type") Integer type,
                                            @Param("aftersaleSn") String aftersaleSn,
                                            @Param("orderSn") String orderSn,
                                            @Param("keyword") String keyword,
                                            @Param("applyTimeMin") String applyTimeMin,
                                            @Param("applyTimeMax") String applyTimeMax,
                                            @Param("orderBy") String orderBy,
                                            @Param("direction") String direction);

    /** 售后对应的退款单（优先成功记录，其次最新） */
    @Select("""
            SELECT id AS refundId, refund_time AS refundTime, status
              FROM litemall_refund
             WHERE aftersale_id = #{aftersaleId} AND deleted = 0
             ORDER BY (status = 1) DESC, id DESC
             LIMIT 1
            """)
    RefundBriefDTO selectRefundBrief(@Param("aftersaleId") Long aftersaleId);
}

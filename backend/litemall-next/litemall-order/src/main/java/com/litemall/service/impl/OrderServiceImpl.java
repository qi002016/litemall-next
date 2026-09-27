package com.litemall.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.UserClient;
import com.litemall.constant.RedisKeys;
import com.litemall.domain.DTO.OrderSubmitDTO;
import com.litemall.domain.PO.Cart;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PO.OrderGoods;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.OrderDetailVO;
import com.litemall.domain.VO.OrderGoodsVO;
import com.litemall.domain.VO.OrderListItemVO;
import com.litemall.domain.VO.OrderSubmitVO;
import com.litemall.domain.dto.AddressInfoDTO;
import com.litemall.domain.dto.CouponCheckDTO;
import com.litemall.domain.dto.CouponUseDTO;
import com.litemall.domain.dto.GoodsProductInfoDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.CartMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderMapper;
import com.litemall.service.IOrderService;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 订单状态（映射关系见 statusText，文档口径为准：101 未付款；102 待发货；201 已发货；301 已收货；401/402 售后） */
    private static final int STATUS_UNPAID = 101;        // 未付款
    private static final int STATUS_UNDELIVERED = 102;   // 已付款/待发货（文档口径）
    private static final int STATUS_CANCELED = 103;      // 已取消（文档未定义，沿用老项目 103 取消语义）
    private static final int STATUS_SHIPPED = 201;       // 已发货
    private static final int STATUS_CONFIRMED = 301;     // 已收货
    private static final int STATUS_REFUND = 401;        // 售后/退款中

    private final CartMapper cartMapper;
    private final OrderGoodsMapper orderGoodsMapper;
    private final GoodsClient goodsClient;
    private final UserClient userClient;
    private final MarketingClient marketingClient;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String createIdemToken() {
        String token = IdUtil.fastSimpleUUID();
        String key = RedisKeys.ORDER_IDEM + token;
        stringRedisTemplate.opsForValue().set(key, "1",
                RedisKeys.ORDER_IDEM_TTL_SECONDS, TimeUnit.SECONDS);
        return token;
    }

    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public OrderSubmitVO submitOrder(OrderSubmitDTO dto) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }

        // 1.幂等校验：GETDEL 一次性 token，不存在则重复提交
        String idemKey = RedisKeys.ORDER_IDEM + dto.getIdemToken();
        String tokenValue = stringRedisTemplate.opsForValue().getAndDelete(idemKey);
        if (StrUtil.isBlank(tokenValue)) {
            throw new BadRequestException("请勿重复提交订单");
        }

        // 2.校验并锁定购物车项（属于当前用户、未删除）
        List<Long> cartIdList = dto.getCartIds();
        List<Cart> carts = cartMapper.selectByIds(
                cartIdList.stream().map(Long::intValue).collect(Collectors.toList()));
        if (carts.isEmpty()) {
            throw new BadRequestException("购物车为空");
        }
        List<Cart> validCarts = carts.stream()
                .filter(c -> c.getUserId().equals(userId.intValue()))
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .toList();
        if (validCarts.size() != cartIdList.size()) {
            throw new BadRequestException("购物车项不存在或已失效");
        }

        // 3.收货地址
        AddressInfoDTO address = userClient.getAddressById(dto.getAddressId());
        if (address == null) {
            throw new BadRequestException("收货地址不存在");
        }

        // 4.校验商品规格并计算商品总额
        Map<Integer, GoodsProductInfoDTO> productMap = new HashMap<>();
        BigDecimal goodsAmount = BigDecimal.ZERO;
        for (Cart cart : validCarts) {
            GoodsProductInfoDTO product = goodsClient.getProductInfo(cart.getProductId().longValue());
            if (product == null) {
                throw new BadRequestException("商品规格不存在");
            }
            productMap.put(cart.getProductId(), product);
            goodsAmount = goodsAmount.add(cart.getPrice().multiply(BigDecimal.valueOf(cart.getNumber())));
        }

        // 5.优惠券试算（用户券记录ID）
        CouponCheckDTO check = new CouponCheckDTO();
        check.setCouponUserId(dto.getCouponId());
        check.setGoodsAmount(goodsAmount);
        BigDecimal couponAmount = marketingClient.checkCoupon(check);

        // 6.运费（当前简化：免运费）
        BigDecimal freight = BigDecimal.ZERO;

        // 7.订单费用
        BigDecimal orderAmount = goodsAmount.add(freight).subtract(couponAmount);
        if (orderAmount.compareTo(BigDecimal.ZERO) < 0) {
            orderAmount = BigDecimal.ZERO;
        }

        // 8.建订单
        LocalDateTime now = LocalDateTime.now();
        Order order = new Order()
                .setUserId(userId.intValue())
                .setOrderSn(generateOrderSn())
                .setOrderStatus(STATUS_UNPAID)
                .setAftersaleStatus(0)
                .setConsignee(address.getName())
                .setMobile(address.getMobile())
                .setAddress(fullAddress(address))
                .setMessage(StrUtil.nullToEmpty(dto.getMessage()))
                .setGoodsPrice(goodsAmount)
                .setFreightPrice(freight)
                .setCouponPrice(couponAmount)
                .setIntegralPrice(BigDecimal.ZERO)
                .setGrouponPrice(BigDecimal.ZERO)
                .setOrderPrice(orderAmount)
                .setActualPrice(orderAmount)
                .setComments(0)
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        save(order);

        // 9.订单商品快照
        for (Cart cart : validCarts) {
            GoodsProductInfoDTO product = productMap.get(cart.getProductId());
            OrderGoods og = new OrderGoods()
                    .setOrderId(order.getId())
                    .setGoodsId(cart.getGoodsId())
                    .setGoodsName(cart.getGoodsName())
                    .setGoodsSn(cart.getGoodsSn())
                    .setProductId(cart.getProductId())
                    .setNumber(cart.getNumber())
                    .setPrice(cart.getPrice())
                    .setSpecifications(cart.getSpecifications())
                    .setPicUrl(cart.getPicUrl())
                    .setComment(0)
                    .setAddTime(now)
                    .setUpdateTime(now)
                    .setDeleted(false);
            orderGoodsMapper.insert(og);
        }

        // 10.扣库存
        for (Cart cart : validCarts) {
            Map<String, Integer> param = new HashMap<>();
            param.put("productId", cart.getProductId());
            param.put("number", cart.getNumber());
            goodsClient.deductStock(param);
        }

        // 11.核销优惠券
        if (dto.getCouponId() != null) {
            CouponUseDTO use = new CouponUseDTO();
            use.setCouponUserId(dto.getCouponId());
            use.setOrderId(order.getId().longValue());
            marketingClient.useCoupon(use);
        }

        // 12.删除购物车项（逻辑删除）
        LocalDateTime updateTime = LocalDateTime.now();
        for (Cart cart : validCarts) {
            cart.setDeleted(true);
            cart.setUpdateTime(updateTime);
            cartMapper.updateById(cart);
        }

        OrderSubmitVO vo = new OrderSubmitVO();
        vo.setOrderId(order.getId().longValue());
        vo.setOrderSn(order.getOrderSn());
        vo.setOrderAmount(orderAmount);
        return vo;
    }

    @Override
    public PageVO<OrderListItemVO> listOrder(Integer pageNum, Integer pageSize, String status) {
        Long userId = UserContext.getUser();
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Page<Order> page = new Page<>(pn, ps);

        Integer statusCode = parseStatus(status);
        Page<Order> orderPage = lambdaQuery()
                .eq(Order::getUserId, userId.intValue())
                .eq(Order::getDeleted, false)
                .eq(statusCode != null, Order::getOrderStatus, statusCode)
                .orderByDesc(Order::getAddTime)
                .page(page);

        List<OrderListItemVO> voList = orderPage.getRecords().stream()
                .map(this::toListItemVO)
                .collect(Collectors.toList());
        return new PageVO<>(orderPage.getTotal(), orderPage.getPages(), voList);
    }

    @Override
    public OrderDetailVO getOrderDetail(Long orderId) {
        Long userId = UserContext.getUser();
        Order order = lambdaQuery()
                .eq(Order::getId, orderId.intValue())
                .eq(Order::getUserId, userId.intValue())
                .eq(Order::getDeleted, false)
                .one();
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }

        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderId(order.getId().longValue());
        vo.setOrderSn(order.getOrderSn());
        vo.setStatus(statusText(order.getOrderStatus()));
        vo.setConsignee(order.getConsignee());
        vo.setMobile(order.getMobile());
        vo.setAddress(order.getAddress());
        vo.setMessage(order.getMessage());
        vo.setGoodsAmount(order.getGoodsPrice());
        vo.setFreightAmount(order.getFreightPrice());
        vo.setCouponAmount(order.getCouponPrice());
        vo.setOrderAmount(order.getOrderPrice());
        vo.setActualPrice(order.getActualPrice());
        vo.setPayTime(order.getPayTime() == null ? null : order.getPayTime().format(DT));
        vo.setShipChannel(order.getShipChannel());
        vo.setShipSn(order.getShipSn());
        vo.setShipTime(order.getShipTime() == null ? null : order.getShipTime().format(DT));
        vo.setAddTime(order.getAddTime() == null ? null : order.getAddTime().format(DT));
        vo.setGoodsList(listOrderGoods(order.getId()));
        return vo;
    }

    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        Long userId = UserContext.getUser();
        Order order = lambdaQuery()
                .eq(Order::getId, orderId.intValue())
                .eq(Order::getUserId, userId.intValue())
                .eq(Order::getDeleted, false)
                .one();
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        if (order.getOrderStatus() != STATUS_UNPAID) {
            throw new BadRequestException("仅未付款订单可取消");
        }

        // 回补库存
        List<OrderGoods> orderGoods = orderGoodsMapper.selectList(
                new LambdaQueryWrapper<OrderGoods>()
                        .eq(OrderGoods::getOrderId, order.getId())
                        .eq(OrderGoods::getDeleted, false));
        for (OrderGoods og : orderGoods) {
            Map<String, Integer> param = new HashMap<>();
            param.put("productId", og.getProductId());
            param.put("number", og.getNumber());
            goodsClient.releaseStock(param);
        }

        // 回退优惠券
        CouponUseDTO rollback = new CouponUseDTO();
        rollback.setOrderId(order.getId().longValue());
        marketingClient.rollbackCoupon(rollback);

        // 订单置为已取消
        order.setOrderStatus(STATUS_CANCELED);
        order.setEndTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long orderId) {
        Long userId = UserContext.getUser();
        Order order = lambdaQuery()
                .eq(Order::getId, orderId.intValue())
                .eq(Order::getUserId, userId.intValue())
                .eq(Order::getDeleted, false)
                .one();
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        if (order.getOrderStatus() != STATUS_SHIPPED) {
            throw new BadRequestException("仅已发货订单可确认收货");
        }
        order.setOrderStatus(STATUS_CONFIRMED);
        order.setConfirmTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);
    }

    /* ==================== 私有方法 ==================== */

    private OrderListItemVO toListItemVO(Order order) {
        OrderListItemVO vo = new OrderListItemVO();
        vo.setOrderId(order.getId().longValue());
        vo.setOrderSn(order.getOrderSn());
        vo.setStatus(statusText(order.getOrderStatus()));
        vo.setGoodsAmount(order.getGoodsPrice());
        vo.setFreightAmount(order.getFreightPrice());
        vo.setCouponAmount(order.getCouponPrice());
        vo.setOrderAmount(order.getOrderPrice());
        vo.setActualPrice(order.getActualPrice());
        vo.setAddTime(order.getAddTime() == null ? null : order.getAddTime().format(DT));

        // 列表商品简述（不含 productId/specifications/commentStatus）
        List<OrderGoods> goodsList = orderGoodsMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderGoods>()
                        .eq(OrderGoods::getOrderId, order.getId())
                        .eq(OrderGoods::getDeleted, false));
        vo.setGoodsList(goodsList.stream().map(og -> {
            OrderGoodsVO g = new OrderGoodsVO();
            g.setGoodsId(og.getGoodsId().longValue());
            g.setGoodsName(og.getGoodsName());
            g.setPicUrl(og.getPicUrl());
            g.setNumber(og.getNumber());
            g.setPrice(og.getPrice());
            return g;
        }).collect(Collectors.toList()));
        return vo;
    }

    private List<OrderGoodsVO> listOrderGoods(Integer orderId) {
        List<OrderGoods> goodsList = orderGoodsMapper.selectList(
                new LambdaQueryWrapper<OrderGoods>()
                        .eq(OrderGoods::getOrderId, orderId)
                        .eq(OrderGoods::getDeleted, false));
        return goodsList.stream().map(og -> {
            OrderGoodsVO g = new OrderGoodsVO();
            g.setGoodsId(og.getGoodsId().longValue());
            g.setGoodsName(og.getGoodsName());
            g.setPicUrl(og.getPicUrl());
            g.setNumber(og.getNumber());
            g.setPrice(og.getPrice());
            g.setProductId(og.getProductId().longValue());
            g.setSpecifications(parseSpecList(og.getSpecifications()));
            g.setCommentStatus(og.getComment() == null ? 0 : og.getComment());
            return g;
        }).collect(Collectors.toList());
    }

    /** specifications 形如 ["红色","XL"]，解析为数组 */
    private List<String> parseSpecList(String json) {
        if (StrUtil.isBlank(json)) {
            return java.util.Collections.emptyList();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (Exception e) {
            return java.util.Collections.singletonList(json);
        }
    }

    /** 拼接完整收货地址 */
    private String fullAddress(AddressInfoDTO address) {
        return StrUtil.nullToEmpty(address.getProvince())
                + StrUtil.nullToEmpty(address.getCity())
                + StrUtil.nullToEmpty(address.getDistrict())
                + StrUtil.nullToEmpty(address.getAddress());
    }

    /** 生成订单编号：yyyyMMddHHmmss + 6位随机 */
    private String generateOrderSn() {
        String sn = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", (int) (Math.random() * 1000000));
        Long count = lambdaQuery().eq(Order::getOrderSn, sn).count();
        if (count != null && count > 0) {
            return generateOrderSn();
        }
        return sn;
    }

    /** 文档状态串 → 老项目 order_status */
    private Integer parseStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return null;
        }
        return switch (status) {
            case "unpaid" -> STATUS_UNPAID;
            case "undelivered" -> STATUS_UNDELIVERED;
            case "shipped" -> STATUS_SHIPPED;
            case "confirmed" -> STATUS_CONFIRMED;
            case "refund" -> STATUS_REFUND;
            default -> null;
        };
    }

    /** 老项目 order_status → 文档状态串 */
    private String statusText(Integer orderStatus) {
        if (orderStatus == null) {
            return null;
        }
        return switch (orderStatus) {
            case STATUS_UNPAID -> "unpaid";
            case STATUS_UNDELIVERED -> "undelivered";
            case STATUS_CANCELED -> "canceled";
            case STATUS_SHIPPED -> "shipped";
            case STATUS_CONFIRMED -> "confirmed";
            case STATUS_REFUND -> "refund";
            default -> "unknown";
        };
    }
}

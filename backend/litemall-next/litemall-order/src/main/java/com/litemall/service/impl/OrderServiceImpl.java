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
import com.litemall.domain.dto.OrderAdminDTO;
import com.litemall.domain.dto.OrderAdminStatsDTO;
import com.litemall.domain.dto.OrderRefundDTO;
import com.litemall.domain.dto.OrderShipDTO;
import com.litemall.domain.dto.StockChangeDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.CartMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderMapper;
import com.litemall.service.IOrderService;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** M3：订单号时间前缀格式 */
    private static final DateTimeFormatter SN_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** M3：进程内自增序列，避免同一秒内随机数碰撞 */
    private static final AtomicLong SN_SEQUENCE = new AtomicLong(ThreadLocalRandom.current().nextLong(0x10000));
    /** M3：order_sn 唯一索引冲突时的最大重试次数（不再递归调用自身） */
    private static final int SN_MAX_RETRY = 3;

    /* ==================== M4：下单幂等 token 状态机 ==================== */
    /** 已签发、可被消费 */
    private static final String IDEM_INIT = "INIT";
    /** 已消费（正在处理或已成功），拒绝重复提交 */
    private static final String IDEM_USED = "USED";
    /** 处理失败已回退，允许用同一 token 重试 */
    private static final String IDEM_ROLLED_BACK = "ROLLED_BACK";
    private static final String IDEM_OK = "OK";

    /**
     * M4：INIT / ROLLED_BACK --(原子)--> USED。
     * 返回 {@code MISSING}（token 不存在或已过期）或 {@code USED}（已被消费）时均视为不允许提交。
     * 用 Lua 保证"判断 + 置位"原子，避免并发下两个请求同时通过。
     */
    private static final DefaultRedisScript<String> IDEM_MARK_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('GET', KEYS[1]) "
                    + "if not v then return 'MISSING' end "
                    + "if v == 'INIT' or v == 'ROLLED_BACK' then "
                    + "  local ttl = redis.call('TTL', KEYS[1]) "
                    + "  if ttl and ttl > 0 then redis.call('SET', KEYS[1], 'USED', 'EX', ttl) "
                    + "  else redis.call('SET', KEYS[1], 'USED') end "
                    + "  return 'OK' "
                    + "end "
                    + "return 'USED'", String.class);

    /** M4：USED --(原子)--> ROLLED_BACK，保留原 TTL；仅当仍处于 USED 态时生效 */
    private static final DefaultRedisScript<Long> IDEM_ROLLBACK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == 'USED' then "
                    + "  local ttl = redis.call('TTL', KEYS[1]) "
                    + "  if ttl and ttl > 0 then redis.call('SET', KEYS[1], 'ROLLED_BACK', 'EX', ttl) "
                    + "  else redis.call('SET', KEYS[1], 'ROLLED_BACK') end "
                    + "  return 1 "
                    + "end "
                    + "return 0", Long.class);

    /** 订单状态（映射关系见 statusText，文档口径为准：101 未付款；102 待发货；201 已发货；301 已收货；401/402 售后） */
    private static final int STATUS_UNPAID = 101;        // 未付款
    private static final int STATUS_UNDELIVERED = 102;   // 已付款/待发货（文档口径）
    private static final int STATUS_CANCELED = 103;      // 已取消（文档未定义，沿用老项目 103 取消语义）
    private static final int STATUS_SHIPPED = 201;       // 已发货
    private static final int STATUS_CONFIRMED = 301;     // 已收货
    private static final int STATUS_REFUND = 401;        // 售后/退款中
    /** 与 pay 服务 {@code PayServiceImpl.STATUS_REFUND_DONE} 同义：退款完成（终态） */
    private static final int STATUS_REFUND_DONE = 402;

    /** H5：管理端发起的退款在 litemall_order.refund_type 上的取值（与 pay 服务写入的 wxpay 区分发起方） */
    private static final String REFUND_TYPE_ADMIN = "admin";

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
        // M4：签发时写入 INIT 态，由 submitOrder 原子推进到 USED；失败回退 ROLLED_BACK 允许重试
        stringRedisTemplate.opsForValue().set(key, IDEM_INIT,
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

        // 1.幂等校验（M4）：只有 INIT/ROLLED_BACK --(原子)--> USED 才放行；
        //    已是 USED 说明请求正在处理或已成功，一律拒绝重复提交。
        String idemKey = RedisKeys.ORDER_IDEM + dto.getIdemToken();
        if (!markIdemUsed(idemKey)) {
            throw new BadRequestException("请勿重复提交订单");
        }
        try {
            return doSubmitOrder(dto, userId);
        } catch (RuntimeException e) {
            // M4：下单失败时把 token 置回可重试状态，前端用同一 token 重试不再被误判为重复提交。
            // 必须重新抛出异常：Seata(GlobalTransactional) 依赖异常触发回滚，切勿在此吞掉。
            rollbackIdemUsed(idemKey);
            throw e;
        }
    }

    /** 真正的下单流程；由 {@link #submitOrder} 在全局事务内调用 */
    private OrderSubmitVO doSubmitOrder(OrderSubmitDTO dto, Long userId) {

        // 2.校验并锁定购物车项（属于当前用户、未删除）
        List<Long> cartIdList = dto.getCartIds();
        List<Cart> carts = cartMapper.selectByIds(cartIdList);
        if (carts.isEmpty()) {
            throw new BadRequestException("购物车为空");
        }
        List<Cart> validCarts = carts.stream()
                .filter(c -> c.getUserId().equals(userId))
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
        Map<Long, GoodsProductInfoDTO> productMap = new HashMap<>();
        BigDecimal goodsAmount = BigDecimal.ZERO;
        for (Cart cart : validCarts) {
            GoodsProductInfoDTO product = goodsClient.getProductInfo(cart.getProductId());
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
                .setUserId(userId)
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
        // M3：经唯一索引兜底的保存（冲突自动换号，最多 3 次）
        saveOrder(order);

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

        // 10.扣库存（M2：一次批量 RPC 完成所有购物车项，避免 N 次跨服务调用）
        List<StockChangeDTO.Item> deductItems = new ArrayList<>(validCarts.size());
        for (Cart cart : validCarts) {
            deductItems.add(stockItem(cart.getProductId(), cart.getNumber()));
        }
        goodsClient.deductStockBatch(stockChange(deductItems));

        // 11.核销优惠券
        if (dto.getCouponId() != null) {
            CouponUseDTO use = new CouponUseDTO();
            use.setCouponUserId(dto.getCouponId());
            use.setOrderId(order.getId());
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
        vo.setOrderId(order.getId());
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
                .eq(Order::getUserId, userId)
                .eq(Order::getDeleted, false)
                .eq(statusCode != null, Order::getOrderStatus, statusCode)
                .orderByDesc(Order::getAddTime)
                .page(page);

        List<Order> orders = orderPage.getRecords();
        // M1：一次 IN 查询取回本页全部订单商品（原先每行单独查 => 页大小 10 时 11 次查询）
        Map<Long, List<OrderGoods>> goodsByOrder = loadOrderGoods(orders);
        List<OrderListItemVO> voList = orders.stream()
                .map(o -> toListItemVO(o, goodsByOrder.getOrDefault(o.getId(), List.of())))
                .collect(Collectors.toList());
        return new PageVO<>(orderPage.getTotal(), orderPage.getPages(), voList);
    }

    @Override
    public OrderDetailVO getOrderDetail(Long orderId) {
        Long userId = UserContext.getUser();
        Order order = lambdaQuery()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .eq(Order::getDeleted, false)
                .one();
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }

        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderId(order.getId());
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
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .eq(Order::getDeleted, false)
                .one();
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        if (order.getOrderStatus() != STATUS_UNPAID) {
            throw new BadRequestException("仅未付款订单可取消");
        }

        // 回补库存 + 回退优惠券（H5：与管理端退款共用同一套补偿逻辑）
        releaseOrderStockAndCoupon(order);

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
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
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

    /* ==================== H5：管理端订单（admin 服务 Feign 调用；状态机在本服务内收口） ==================== */

    @Override
    public PageVO<OrderAdminDTO> adminListOrders(Integer pageNum, Integer pageSize, String status) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Integer statusCode = parseStatus(status);
        Page<Order> orderPage = lambdaQuery()
                .eq(Order::getDeleted, false)
                .eq(statusCode != null, Order::getOrderStatus, statusCode)
                .orderByDesc(Order::getId)
                .page(new Page<>(pn, ps));
        // 字段与改造前 admin 端的 Map 保持一致：id/orderSn/userId/orderAmount/status/addTime
        return PageVO.of(orderPage, o -> {
            OrderAdminDTO dto = new OrderAdminDTO();
            dto.setId(o.getId());
            dto.setOrderSn(o.getOrderSn());
            dto.setUserId(o.getUserId());
            dto.setOrderAmount(o.getOrderPrice());
            dto.setStatus(statusText(o.getOrderStatus()));
            dto.setAddTime(o.getAddTime());
            return dto;
        });
    }

    @Override
    public OrderAdminStatsDTO adminOrderStats() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        OrderAdminStatsDTO stats = new OrderAdminStatsDTO();
        stats.setTodayOrders(lambdaQuery()
                .eq(Order::getDeleted, false)
                .ge(Order::getAddTime, today)
                .count());
        stats.setUndelivered(lambdaQuery()
                .eq(Order::getDeleted, false)
                .eq(Order::getOrderStatus, STATUS_UNDELIVERED)
                .count());
        return stats;
    }

    /**
     * H5：管理端发货。状态机：仅 102 待发货 → 201 已发货。
     * <p>
     * 拒绝 101 未付款、103 已取消、201 重复发货、301 已收货、401/402 售后，
     * 从根上替代改造前 admin 端"直接置 201"的写法。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminShip(OrderShipDTO dto) {
        Order order = requireOrder(dto == null ? null : dto.getOrderId());
        if (order.getOrderStatus() == null || order.getOrderStatus() != STATUS_UNDELIVERED) {
            throw new BadRequestException("仅待发货订单可发货，当前状态：" + statusText(order.getOrderStatus()));
        }
        LocalDateTime now = LocalDateTime.now();
        order.setOrderStatus(STATUS_SHIPPED);
        if (StrUtil.isNotBlank(dto.getShipChannel())) {
            order.setShipChannel(dto.getShipChannel());
        }
        if (StrUtil.isNotBlank(dto.getShipSn())) {
            order.setShipSn(dto.getShipSn());
        }
        order.setShipTime(now);
        order.setUpdateTime(now);
        updateById(order);
    }

    /**
     * H5：管理端退款。状态机：102 待发货 / 201 已发货 / 301 已收货 → 401 售后；
     * 401 幂等（已在售后流程中，直接成功返回，避免库存被重复回补）；
     * 101 未付款、103 已取消、402 已退款完成一律拒绝。
     * <p>
     * 补偿与 {@link #cancelOrder(Long)} 复用同一套逻辑（M2 批量回补库存 + 券回退），
     * 不再出现改造前"admin 只改状态、库存与券被绕过"的问题。
     */
    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public void adminRefund(OrderRefundDTO dto) {
        Order order = requireOrder(dto == null ? null : dto.getOrderId());
        Integer status = order.getOrderStatus();
        if (status != null && status == STATUS_REFUND) {
            // 幂等：重复提交不重复补偿
            return;
        }
        if (status == null || (status != STATUS_UNDELIVERED
                && status != STATUS_SHIPPED
                && status != STATUS_CONFIRMED)) {
            String current = statusText(status);
            if (status != null && status == STATUS_UNPAID) {
                throw new BadRequestException("未付款订单无需退款，请取消订单，当前状态：" + current);
            }
            if (status != null && status == STATUS_CANCELED) {
                throw new BadRequestException("订单已取消，不可退款，当前状态：" + current);
            }
            if (status != null && status == STATUS_REFUND_DONE) {
                throw new BadRequestException("订单已退款完成，不可重复退款，当前状态：" + current);
            }
            throw new BadRequestException("当前订单状态不可退款：" + current);
        }

        // 库存回补 + 优惠券回退（与取消订单同一套既有逻辑）
        releaseOrderStockAndCoupon(order);

        LocalDateTime now = LocalDateTime.now();
        order.setOrderStatus(STATUS_REFUND);
        order.setRefundAmount(dto.getRefundAmount() != null ? dto.getRefundAmount() : order.getActualPrice());
        order.setRefundType(REFUND_TYPE_ADMIN);
        order.setRefundContent(dto.getRefundContent());
        order.setRefundTime(now);
        order.setUpdateTime(now);
        updateById(order);
    }

    /** H5：管理端接口的订单必存在校验（不存在/已删除一律拒绝） */
    private Order requireOrder(Long orderId) {
        if (orderId == null) {
            throw new BadRequestException("订单ID不能为空");
        }
        Order order = getById(orderId);
        if (order == null || Boolean.TRUE.equals(order.getDeleted())) {
            throw new BadRequestException("订单不存在");
        }
        return order;
    }

    /**
     * H5：回补订单库存并回退优惠券。
     * <p>
     * 由 {@link #cancelOrder(Long)}（原实现内联于此）与管理端退款共用：
     * 库存走 M2 的批量 RPC，优惠券回退由 marketing 服务按 orderId 反查，均不新增交互方式。
     */
    private void releaseOrderStockAndCoupon(Order order) {
        // 回补库存（M2：一次批量 RPC）
        List<OrderGoods> orderGoods = orderGoodsMapper.selectList(
                new LambdaQueryWrapper<OrderGoods>()
                        .eq(OrderGoods::getOrderId, order.getId())
                        .eq(OrderGoods::getDeleted, false));
        if (!orderGoods.isEmpty()) {
            List<StockChangeDTO.Item> releaseItems = new ArrayList<>(orderGoods.size());
            for (OrderGoods og : orderGoods) {
                releaseItems.add(stockItem(og.getProductId(), og.getNumber()));
            }
            goodsClient.releaseStockBatch(stockChange(releaseItems));
        }

        // 回退优惠券（couponUserId 为空时 marketing 服务按 orderId 反查）
        CouponUseDTO rollback = new CouponUseDTO();
        rollback.setOrderId(order.getId());
        marketingClient.rollbackCoupon(rollback);
    }

    /* ==================== 私有方法 ==================== */

    private OrderListItemVO toListItemVO(Order order, List<OrderGoods> goodsList) {
        OrderListItemVO vo = new OrderListItemVO();
        vo.setOrderId(order.getId());
        vo.setOrderSn(order.getOrderSn());
        vo.setStatus(statusText(order.getOrderStatus()));
        vo.setGoodsAmount(order.getGoodsPrice());
        vo.setFreightAmount(order.getFreightPrice());
        vo.setCouponAmount(order.getCouponPrice());
        vo.setOrderAmount(order.getOrderPrice());
        vo.setActualPrice(order.getActualPrice());
        vo.setAddTime(order.getAddTime() == null ? null : order.getAddTime().format(DT));

        // 列表商品简述（不含 productId/specifications/commentStatus）；goodsList 由 listOrder 批量预取
        vo.setGoodsList(goodsList.stream().map(og -> {
            OrderGoodsVO g = new OrderGoodsVO();
            g.setGoodsId(og.getGoodsId());
            g.setGoodsName(og.getGoodsName());
            g.setPicUrl(og.getPicUrl());
            g.setNumber(og.getNumber());
            g.setPrice(og.getPrice());
            return g;
        }).collect(Collectors.toList()));
        return vo;
    }

    /** M1：批量查询本页订单的商品行并按 orderId 分组，消除订单列表的 N+1 查询 */
    private Map<Long, List<OrderGoods>> loadOrderGoods(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return Map.of();
        }
        List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
        List<OrderGoods> all = orderGoodsMapper.selectList(new LambdaQueryWrapper<OrderGoods>()
                .in(OrderGoods::getOrderId, orderIds)
                .eq(OrderGoods::getDeleted, false));
        return all.stream().collect(Collectors.groupingBy(OrderGoods::getOrderId));
    }

    private List<OrderGoodsVO> listOrderGoods(Long orderId) {
        List<OrderGoods> goodsList = orderGoodsMapper.selectList(
                new LambdaQueryWrapper<OrderGoods>()
                        .eq(OrderGoods::getOrderId, orderId)
                        .eq(OrderGoods::getDeleted, false));
        return goodsList.stream().map(og -> {
            OrderGoodsVO g = new OrderGoodsVO();
            g.setGoodsId(og.getGoodsId());
            g.setGoodsName(og.getGoodsName());
            g.setPicUrl(og.getPicUrl());
            g.setNumber(og.getNumber());
            g.setPrice(og.getPrice());
            g.setProductId(og.getProductId());
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

    /**
     * 生成订单编号（M3）：{@code yyyyMMddHHmmss}(14) + 4 位进程内序列(hex) + 4 位随机(hex)，共 22 位。
     * <p>
     * 原实现"count 查重 + 冲突时递归调用自身"存在两个问题：并发下仍会重号（查重与插入非原子），
     * 且冲突时递归无上界。现在改为低碰撞率的组合号 + 由 DB 唯一索引兜底（见 saveOrder）。
     */
    private String generateOrderSn() {
        long seq = SN_SEQUENCE.getAndIncrement() & 0xFFFFL;
        int rand = ThreadLocalRandom.current().nextInt(0x10000);
        return LocalDateTime.now().format(SN_FORMAT) + String.format("%04x%04x", seq, rand);
    }

    /**
     * M3：保存订单；若 {@code order_sn} 唯一索引冲突则换号重试（最多 {@link #SN_MAX_RETRY} 次）。
     * <p>
     * 依赖 DDL：{@code litemall_order} 增加 {@code uk_order_sn(order_sn)}（见交付报告"需人工确认"项）。
     * 未加索引时该方法等价于直接 save（不会误伤正常路径）。
     */
    private void saveOrder(Order order) {
        for (int attempt = 0; ; attempt++) {
            try {
                save(order);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt >= SN_MAX_RETRY - 1) {
                    throw e;
                }
                log.warn("[M3] 订单号冲突，换号重试。attempt={}, sn={}", attempt + 1, order.getOrderSn());
                order.setOrderSn(generateOrderSn());
            }
        }
    }

    /* ==================== M4：幂等 token 状态机 ==================== */

    /** INIT / ROLLED_BACK --(原子)--> USED；返回 false 表示 token 不存在/已过期/已被消费 */
    private boolean markIdemUsed(String idemKey) {
        String result;
        try {
            result = stringRedisTemplate.execute(IDEM_MARK_SCRIPT, List.of(idemKey));
        } catch (Exception e) {
            log.error("[M4] 幂等 token 状态推进失败, key={}", idemKey, e);
            throw new BadRequestException("订单提交校验失败，请稍后重试");
        }
        return IDEM_OK.equals(result);
    }

    /** USED --(原子)--> ROLLED_BACK（保留原 TTL）；失败不影响主流程 */
    private void rollbackIdemUsed(String idemKey) {
        try {
            stringRedisTemplate.execute(IDEM_ROLLBACK_SCRIPT, List.of(idemKey));
        } catch (Exception e) {
            log.warn("[M4] 幂等 token 回退失败（该 token 将在 TTL 到期后才能重试）, key={}", idemKey, e);
        }
    }

    /** M2：构造单个库存变更项 */
    private StockChangeDTO.Item stockItem(Long productId, Integer number) {
        StockChangeDTO.Item item = new StockChangeDTO.Item();
        item.setProductId(productId);
        item.setNumber(number);
        return item;
    }

    /** M2：构造批量库存变更请求体 */
    private StockChangeDTO stockChange(List<StockChangeDTO.Item> items) {
        StockChangeDTO dto = new StockChangeDTO();
        dto.setItems(items);
        return dto;
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
            // H5：402 退款完成与 401 对外同样呈现为"退款"，对齐 pay 服务的 mapStatus(refunded)
            case STATUS_REFUND_DONE -> "refund";
            default -> "unknown";
        };
    }
}

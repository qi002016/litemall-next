package com.litemall.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.GoodsClient;
import com.litemall.client.MarketingClient;
import com.litemall.client.UserClient;
import com.litemall.constant.AftersaleStatus;
import com.litemall.constant.OrderStatus;
import com.litemall.constant.RedisKeys;
import com.litemall.domain.DTO.OrderConfirmDTO;
import com.litemall.domain.DTO.OrderSubmitDTO;
import com.litemall.domain.PO.Aftersale;
import com.litemall.domain.PO.Cart;
import com.litemall.domain.PO.Order;
import com.litemall.domain.PO.OrderGoods;
import com.litemall.domain.PO.OrderLog;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.OrderAftersaleBriefVO;
import com.litemall.domain.VO.OrderConfirmLineVO;
import com.litemall.domain.VO.OrderConfirmVO;
import com.litemall.domain.VO.OrderDetailLineVO;
import com.litemall.domain.VO.OrderDetailVO;
import com.litemall.domain.VO.OrderGoodsVO;
import com.litemall.domain.VO.OrderListItemVO;
import com.litemall.domain.VO.OrderSubmitVO;
import com.litemall.domain.VO.OrderTimelineVO;
import com.litemall.domain.dto.AddressInfoDTO;
import com.litemall.domain.dto.CloseExpiredResultDTO;
import com.litemall.domain.dto.CouponCheckDTO;
import com.litemall.domain.dto.CouponUseDTO;
import com.litemall.domain.dto.DeductBatchResultDTO;
import com.litemall.domain.dto.GoodsProductInfoDTO;
import com.litemall.domain.dto.LockedStockDTO;
import com.litemall.domain.dto.OrderAdminDTO;
import com.litemall.domain.dto.OrderAdminDetailDTO;
import com.litemall.domain.dto.OrderAdminStatsDTO;
import com.litemall.domain.dto.OrderCloseDTO;
import com.litemall.domain.dto.OrderGoodsStatusDTO;
import com.litemall.domain.dto.OrderShipDTO;
import com.litemall.domain.dto.PaySuccessDTO;
import com.litemall.domain.dto.PaySuccessResultDTO;
import com.litemall.domain.dto.StockChangeDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ConflictException;
import com.litemall.exception.NotFoundException;
import com.litemall.exception.UnauthorizedException;
import com.litemall.mapper.AftersaleMapper;
import com.litemall.mapper.CartMapper;
import com.litemall.mapper.OrderGoodsMapper;
import com.litemall.mapper.OrderLogMapper;
import com.litemall.mapper.OrderMapper;
import com.litemall.service.IOrderService;
import com.litemall.support.Money;
import com.litemall.support.OrderRules;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * <p>
 * 订单表 服务实现类（R1：锁量下单、状态机 CAS、三种关闭统一释放、后台订单域收口）
 * </p>
 * <p>
 * 关键口径（docs/impl/03-R1实施口径.md / 07 接口契约 v4）：
 * <ul>
 *   <li><b>下单只锁量不扣库存</b>：提交订单只落订单行（{@code order_status=101} 即锁定量来源），
 *       实扣唯一时点是支付成功（CI-97 markPaid）；库存快照卖不卖得动由 goods 侧
 *       {@code number − locked} 派生，本服务不加任何 locked 列。</li>
 *   <li><b>三种关闭同一套动作</b>：CI-85 用户取消 / CN-11 超时 / CN-10 运营关单，都走
 *       {@link #closeUnpaidOrder}：CAS 101→103 + {@code close_type} + {@code end_time} + 释放锁量与券。</li>
 *   <li><b>状态跃迁一律 CAS</b>（{@code eq(order_status, X)} 条件更新），affected=0 → 409，
 *       不允许"读-判-写"的非原子写法。</li>
 *   <li><b>501 已完成 R1 不可达</b>：唯一写点在 R2 CI-36 评价成功后的 CAS（见
 *       {@link OrderRules#canMarkCompleted}），本服务只读不写。</li>
 * </ul>
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

    /** 未付款自动关闭时长（BR-ORDER-06 / 07 §2.2） */
    private static final long EXPIRE_MINUTES = 30L;

    /** 单笔订单最多行数（CI-80/CI-82 ORDER_ITEM_LIMIT） */
    private static final int MAX_ITEM_LINES = 50;

    /** 售后申请窗口（天，P-36：confirm_time + 7 天） */
    private static final long AFTERSALE_WINDOW_DAYS = 7L;

    /* ==================== 错误码（03 §4.3，只允许用登记过的码） ==================== */
    private static final String CODE_ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    private static final String CODE_ORDER_STATUS_ILLEGAL = "ORDER_STATUS_ILLEGAL";
    private static final String CODE_STOCK_NOT_ENOUGH = "STOCK_NOT_ENOUGH";
    private static final String CODE_ADDRESS_INVALID = "ADDRESS_INVALID";
    private static final String CODE_COUPON_NOT_USABLE = "COUPON_NOT_USABLE";
    private static final String CODE_ORDER_ITEM_LIMIT = "ORDER_ITEM_LIMIT";
    private static final String CODE_IDEMPOTENT_CONFLICT = "IDEMPOTENT_CONFLICT";
    private static final String CODE_AFTERSALE_EXISTS = "AFTERSALE_EXISTS";
    private static final String CODE_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";

    /* ==================== 日志 action（litemall_log.action ≤45 字符） ==================== */
    private static final String LOG_CLOSE = "order:close";
    private static final String LOG_CLOSE_EXPIRED = "order:close-expired";
    private static final String LOG_SHIP_UPDATE = "order:ship-update";
    private static final String LOG_TARGET_TYPE = "order";

    /** M3：订单号时间前缀格式 */
    private static final DateTimeFormatter SN_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** M3：进程内自增序列，避免同一秒内随机数碰撞 */
    private static final AtomicLong SN_SEQUENCE = new AtomicLong(ThreadLocalRandom.current().nextLong(0x10000));
    /** M3：order_sn 唯一索引冲突时的最大重试次数（不再递归调用自身） */
    private static final int SN_MAX_RETRY = 3;

    /* ==================== M4：下单幂等 token 状态机 ==================== */
    private static final String IDEM_INIT = "INIT";
    private static final String IDEM_USED = "USED";
    private static final String IDEM_ROLLED_BACK = "ROLLED_BACK";
    private static final String IDEM_OK = "OK";
    private static final String IDEM_MISSING = "MISSING";
    /** 幂等「首次结果」缓存：order:idem:req:{token} → "参数指纹|OrderSubmitVO JSON" */
    private static final String IDEM_REQ_PREFIX = "order:idem:req:";

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

    private static final DefaultRedisScript<Long> IDEM_ROLLBACK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == 'USED' then "
                    + "  local ttl = redis.call('TTL', KEYS[1]) "
                    + "  if ttl and ttl > 0 then redis.call('SET', KEYS[1], 'ROLLED_BACK', 'EX', ttl) "
                    + "  else redis.call('SET', KEYS[1], 'ROLLED_BACK') end "
                    + "  return 1 "
                    + "end "
                    + "return 0", Long.class);

    /**
     * CI-83「待评价」栏：301 且存在「未退款且未评价」行。
     * <p>comment=-1（不可评价）不参与判定（03 Q5）；R1 不写 -1。</p>
     */
    private static final String SQL_UNREVIEWED =
            "SELECT 1 FROM litemall_order_goods og WHERE og.order_id = litemall_order.id "
                    + "AND og.deleted = 0 AND og.refund_status = 0 AND (og.comment IS NULL OR og.comment = 0)";

    /** fullyRefunded = NOT EXISTS(og.deleted=0 AND refund_status&lt;&gt;2)（03 Q5 原文） */
    private static final String SQL_NOT_FULLY_REFUNDED =
            "SELECT 1 FROM litemall_order_goods og WHERE og.order_id = litemall_order.id "
                    + "AND og.deleted = 0 AND og.refund_status <> 2";

    /** 存在未完结售后（0/1/2/3） */
    private static final String SQL_UNFINISHED_AFTERSALE =
            "SELECT 1 FROM litemall_aftersale a WHERE a.order_id = litemall_order.id "
                    + "AND a.deleted = 0 AND a.status IN (0, 1, 2, 3)";

    private final CartMapper cartMapper;
    private final OrderGoodsMapper orderGoodsMapper;
    private final AftersaleMapper aftersaleMapper;
    private final OrderLogMapper orderLogMapper;
    private final GoodsClient goodsClient;
    private final UserClient userClient;
    private final MarketingClient marketingClient;
    private final StringRedisTemplate stringRedisTemplate;

    /* ==================================================================== */
    /* ==================== C 端：CI-80 / CI-82 / CI-83 ==================== */
    /* ==================================================================== */

    @Override
    public String createIdemToken() {
        String token = IdUtil.fastSimpleUUID();
        stringRedisTemplate.opsForValue().set(RedisKeys.ORDER_IDEM + token,
                IDEM_INIT, RedisKeys.ORDER_IDEM_TTL_SECONDS, TimeUnit.SECONDS);
        return token;
    }

    @Override
    public OrderConfirmVO confirmOrder(OrderConfirmDTO dto) {
        Long userId = requireUser();
        List<DraftLine> drafts = resolveDrafts(userId, dto.getCartIds(), dto.getBuyNow());
        if (drafts.size() > MAX_ITEM_LINES) {
            throw new BadRequestException("订单商品种类不能超过 " + MAX_ITEM_LINES + " 种", CODE_ORDER_ITEM_LIMIT);
        }
        requireAddress(dto.getAddressId());

        BigDecimal goodsAmount = sumAmount(drafts);
        BigDecimal couponAmount = resolveCouponAmount(userId, dto.getCouponId(), goodsAmount);
        List<BigDecimal> shares = OrderRules.allocateCoupon(lineAmounts(drafts), couponAmount);

        OrderConfirmVO vo = new OrderConfirmVO();
        vo.setGoodsAmount(Money.fen(goodsAmount));
        vo.setFreightAmount(0);
        vo.setCouponAmount(Money.fen(couponAmount));
        vo.setOrderAmount(Money.fen(goodsAmount.subtract(couponAmount).max(BigDecimal.ZERO)));
        List<OrderConfirmLineVO> lines = new ArrayList<>(drafts.size());
        for (int i = 0; i < drafts.size(); i++) {
            DraftLine d = drafts.get(i);
            BigDecimal lineAmount = d.price().multiply(BigDecimal.valueOf(d.number()));
            BigDecimal share = shares.get(i);
            OrderConfirmLineVO line = new OrderConfirmLineVO();
            line.setProductId(d.productId());
            line.setGoodsName(d.goodsName());
            line.setSpecText(d.specText());
            line.setPrice(Money.fen(d.price()));
            line.setNumber(d.number());
            line.setLineAmount(Money.fen(lineAmount));
            line.setCouponShare(Money.fen(share));
            line.setActualAmount(Money.fen(lineAmount.subtract(share)));
            // 可售不足只提示不阻断（07 §2.1）
            line.setAvailableStock(d.sellable());
            lines.add(line);
        }
        vo.setLines(lines);
        return vo;
    }

    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public OrderSubmitVO submitOrder(OrderSubmitDTO dto) {
        Long userId = requireUser();
        String token = StrUtil.trimToNull(dto.getIdemToken());
        if (token == null) {
            throw new BadRequestException("幂等token不能为空", BadRequestException.PARAM_INVALID);
        }
        String idemKey = RedisKeys.ORDER_IDEM + token;
        String reqKey = IDEM_REQ_PREFIX + token;
        String fingerprint = fingerprint(dto);

        String state;
        try {
            state = stringRedisTemplate.execute(IDEM_MARK_SCRIPT, List.of(idemKey));
        } catch (Exception e) {
            log.error("[R1] 幂等 token 状态推进失败, key={}", idemKey, e);
            throw new BadRequestException("订单提交校验失败，请稍后重试", BadRequestException.PARAM_INVALID);
        }
        if (state == null || IDEM_MISSING.equals(state)) {
            throw new BadRequestException("幂等token无效或已过期", BadRequestException.PARAM_INVALID);
        }
        if (IDEM_USED.equals(state)) {
            // 同键同参 → 回放首次结果；同键不同参 → 409 IDEMPOTENT_CONFLICT（07 §2.2）
            OrderSubmitVO replay = replayIdemResult(reqKey, fingerprint);
            if (replay != null) {
                return replay;
            }
            throw new ConflictException("幂等token已用于不同参数的提交，请刷新后重试", CODE_IDEMPOTENT_CONFLICT);
        }

        OrderSubmitVO vo;
        try {
            vo = doSubmitOrder(dto, userId);
        } catch (RuntimeException e) {
            rollbackIdemUsed(idemKey);
            throw e;
        }
        rememberIdemResult(reqKey, fingerprint, vo);
        return vo;
    }

    /**
     * CI-82 主流程：校验 → 建单 + 行快照（含券额分摊）→ 核销券 → 清购物车。
     * <p><b>不动 goods_product.number</b>：锁量完全由 {@code order_status=101} 派生。</p>
     */
    private OrderSubmitVO doSubmitOrder(OrderSubmitDTO dto, Long userId) {
        List<DraftLine> drafts = resolveDrafts(userId, dto.getCartIds(), dto.getBuyNow());
        if (drafts.size() > MAX_ITEM_LINES) {
            throw new BadRequestException("订单商品种类不能超过 " + MAX_ITEM_LINES + " 种", CODE_ORDER_ITEM_LIMIT);
        }
        AddressInfoDTO address = requireAddress(dto.getAddressId());

        // 可售校验：任一不足整单失败（不允许部分行成功）
        assertSellable(drafts);

        BigDecimal goodsAmount = sumAmount(drafts);
        BigDecimal couponAmount = resolveCouponAmount(userId, dto.getCouponId(), goodsAmount);
        BigDecimal orderAmount = goodsAmount.subtract(couponAmount).max(BigDecimal.ZERO);
        List<BigDecimal> shares = OrderRules.allocateCoupon(lineAmounts(drafts), couponAmount);

        LocalDateTime now = LocalDateTime.now();
        Order order = new Order()
                .setUserId(userId)
                .setOrderSn(generateOrderSn())
                .setOrderStatus(OrderStatus.UNPAID)
                .setAftersaleStatus(0)
                .setConsignee(address.getName())
                .setMobile(address.getMobile())
                .setAddress(fullAddress(address))
                .setMessage(StrUtil.nullToEmpty(dto.getMessage()))
                .setGoodsPrice(goodsAmount)
                .setFreightPrice(BigDecimal.ZERO)
                .setCouponPrice(couponAmount)
                .setIntegralPrice(BigDecimal.ZERO)
                .setGrouponPrice(BigDecimal.ZERO)
                .setOrderPrice(orderAmount)
                .setActualPrice(orderAmount)
                .setComments(0)
                .setMerchantId(1L)
                .setUserDeleted(false)
                .setExpireTime(now.plusMinutes(EXPIRE_MINUTES))
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        saveOrder(order);

        for (int i = 0; i < drafts.size(); i++) {
            DraftLine d = drafts.get(i);
            BigDecimal lineAmount = d.price().multiply(BigDecimal.valueOf(d.number()));
            BigDecimal share = shares.get(i);
            OrderGoods og = new OrderGoods()
                    .setOrderId(order.getId())
                    .setGoodsId(d.goodsId())
                    .setGoodsName(d.goodsName())
                    .setGoodsSn(d.goodsSn())
                    .setProductId(d.productId())
                    .setNumber(d.number())
                    .setPrice(d.price())
                    .setSpecifications(d.specifications())
                    .setPicUrl(d.picUrl())
                    .setSkuCode(d.skuCode())
                    .setCouponShare(share)
                    .setActualAmount(lineAmount.subtract(share))
                    .setRefundStatus(0)
                    .setRefundedNumber(0)
                    .setComment(0)
                    .setAddTime(now)
                    .setUpdateTime(now)
                    .setDeleted(false);
            orderGoodsMapper.insert(og);
        }

        if (dto.getCouponId() != null && couponAmount.signum() > 0) {
            CouponUseDTO use = new CouponUseDTO();
            use.setCouponUserId(dto.getCouponId());
            use.setOrderId(order.getId());
            marketingClient.useCoupon(use);
        }

        // 购物车下单成功后清空已购项；立即购买无需清理
        if (dto.getBuyNow() == null && dto.getCartIds() != null && !dto.getCartIds().isEmpty()) {
            for (Long cartId : distinct(dto.getCartIds())) {
                Cart cart = new Cart().setId(cartId).setDeleted(true).setUpdateTime(now);
                cartMapper.updateById(cart);
            }
        }

        OrderSubmitVO vo = new OrderSubmitVO();
        vo.setOrderId(order.getId());
        vo.setOrderSn(order.getOrderSn());
        vo.setOrderAmount(Money.fen(orderAmount));
        vo.setExpireTime(order.getExpireTime().format(DT));
        return vo;
    }

    @Override
    public PageVO<OrderListItemVO> listOrder(String tab, String status, Integer pageNo, Integer pageSize,
                                             String sortBy, Boolean isAsc) {
        Long userId = requireUser();
        int pn = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);

        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getUserId, userId)
                .eq(Order::getDeleted, false)
                // user_deleted 为 null 的存量行按"未隐藏"处理
                .and(w -> w.isNull(Order::getUserDeleted).or().eq(Order::getUserDeleted, false));

        if (StrUtil.isNotBlank(tab)) {
            applyTab(qw, tab.trim());
        } else if (StrUtil.isNotBlank(status)) {
            List<Integer> codes = OrderStatus.parseMulti(status);
            if (codes == null) {
                throw new BadRequestException("status 取值非法", BadRequestException.PARAM_INVALID);
            }
            if (!codes.isEmpty()) {
                qw.in(Order::getOrderStatus, codes);
            }
        }

        boolean asc = Boolean.TRUE.equals(isAsc);
        if ("orderAmount".equalsIgnoreCase(StrUtil.trimToEmpty(sortBy))) {
            qw.orderBy(true, asc, Order::getOrderPrice);
        } else {
            qw.orderBy(true, asc, Order::getAddTime);
        }
        qw.orderBy(true, asc, Order::getId);

        Page<Order> page = page(new Page<>(pn, ps), qw);
        Map<Long, List<OrderGoods>> goodsMap = loadOrderGoods(page.getRecords());
        List<OrderListItemVO> rows = page.getRecords().stream()
                .map(o -> toListItemVO(o, goodsMap.getOrDefault(o.getId(), List.of())))
                .collect(Collectors.toList());
        return PageVO.of(page, rows);
    }

    /**
     * CI-83 的 7 栏判定（03 Q5 冻结；按单判定，不做记忆化）。
     * <p>tab 与 status 同传时 tab 优先；tab 非法 → 400 PARAM_INVALID（列表永不 404）。</p>
     */
    private void applyTab(LambdaQueryWrapper<Order> qw, String tab) {
        switch (tab) {
            case "all" -> {
                // 不加状态条件，仅排除 user_deleted=1
            }
            case "unpaid" -> qw.eq(Order::getOrderStatus, OrderStatus.UNPAID);
            case "undelivered" -> qw.eq(Order::getOrderStatus, OrderStatus.UNDELIVERED);
            case "shipped" -> qw.eq(Order::getOrderStatus, OrderStatus.SHIPPED);
            case "unreviewed" -> {
                qw.eq(Order::getOrderStatus, OrderStatus.CONFIRMED);
                qw.exists(SQL_UNREVIEWED);
            }
            case "finished" -> qw.and(w -> w.eq(Order::getOrderStatus, OrderStatus.COMPLETED)
                    .or(x -> x.eq(Order::getOrderStatus, OrderStatus.CONFIRMED)
                            // 语义：exists(SQL_NOT_FULLY_REFUNDED) = 「至少还有一行未全退」才进「已完成」；
                            // 取反成 notExists 得到的是 fullyRefunded（全退单），会误入「已完成」——勿再取反。
                            .exists(SQL_NOT_FULLY_REFUNDED)
                            .notExists(SQL_UNREVIEWED)));
            case "aftersale" -> qw.and(w -> w.exists(SQL_UNFINISHED_AFTERSALE)
                    .or().notExists(SQL_NOT_FULLY_REFUNDED));
            default -> throw new BadRequestException("tab 取值非法", BadRequestException.PARAM_INVALID);
        }
    }

    @Override
    public OrderDetailVO getOrderDetail(Long orderId) {
        Long userId = requireUser();
        Order order = orderMapper()
                .selectOne(new LambdaQueryWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .eq(Order::getUserId, userId)
                        .eq(Order::getDeleted, false));
        if (order == null) {
            // 他人订单同样按 404，不泄露存在性（P-13）
            throw new NotFoundException("订单不存在", CODE_ORDER_NOT_FOUND);
        }
        List<OrderGoods> lines = listGoods(order.getId());
        List<Aftersale> unfinished = listUnfinishedAftersale(order.getId());

        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrderId(order.getId());
        vo.setOrderSn(order.getOrderSn());
        vo.setStatus(OrderStatus.ident(order.getOrderStatus()));
        vo.setStatusText(OrderStatus.label(order.getOrderStatus()));
        vo.setConsignee(order.getConsignee());
        vo.setMobile(order.getMobile());
        vo.setAddress(order.getAddress());
        vo.setMessage(order.getMessage());
        vo.setGoodsAmount(Money.fen(order.getGoodsPrice()));
        vo.setFreightAmount(Money.fen(order.getFreightPrice()));
        vo.setCouponAmount(Money.fen(order.getCouponPrice()));
        vo.setOrderAmount(Money.fen(order.getOrderPrice()));
        vo.setActualPrice(Money.fen(order.getActualPrice()));
        vo.setPayTime(format(order.getPayTime()));
        vo.setShipChannel(order.getShipChannel());
        vo.setShipSn(order.getShipSn());
        vo.setShipTime(format(order.getShipTime()));
        vo.setExpireTime(Objects.equals(order.getOrderStatus(), OrderStatus.UNPAID)
                ? format(order.getExpireTime()) : null);
        vo.setAddTime(format(order.getAddTime()));
        vo.setFullyRefunded(OrderRules.isFullyRefunded(lines));
        vo.setRefundedAmount(Money.fen(OrderRules.refundedAmount(lines)));
        vo.setLines(buildDetailLines(order, lines, unfinished));
        vo.setTimeline(buildTimeline(order));
        vo.setAftersale(briefOf(latestUnfinished(unfinished)));
        vo.setGoodsList(listOrderGoods(lines));
        return vo;
    }

    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        Long userId = requireUser();
        Order order = requireOwnOrder(orderId, userId);
        if (!Objects.equals(order.getOrderStatus(), OrderStatus.UNPAID)) {
            throw new ConflictException("该订单当前状态不可取消", CODE_ORDER_STATUS_ILLEGAL);
        }
        closeUnpaidOrder(order, OrderStatus.CLOSE_USER, null, null, null, "该订单当前状态不可取消");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long orderId) {
        Long userId = requireUser();
        Order order = requireOwnOrder(orderId, userId);
        LocalDateTime now = LocalDateTime.now();
        boolean ok = lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getOrderStatus, OrderStatus.SHIPPED)
                .eq(Order::getDeleted, false)
                .set(Order::getOrderStatus, OrderStatus.CONFIRMED)
                .set(Order::getConfirmTime, now)
                .set(Order::getUpdateTime, now)
                .update();
        if (!ok) {
            throw new ConflictException("只有已发货订单可以确认收货", CODE_ORDER_STATUS_ILLEGAL);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void hideOrder(Long orderId) {
        Long userId = requireUser();
        Order order = requireOwnOrder(orderId, userId);
        if (Boolean.TRUE.equals(order.getUserDeleted())) {
            return; // 重复隐藏 200
        }
        long unfinished = countUnfinishedAftersale(order.getId());
        if (unfinished > 0) {
            throw new ConflictException("有处理中的售后，暂不能删除", CODE_AFTERSALE_EXISTS);
        }
        boolean ok = lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getDeleted, false)
                .and(w -> w.isNull(Order::getUserDeleted).or().eq(Order::getUserDeleted, false))
                .set(Order::getUserDeleted, true)
                .set(Order::getUpdateTime, LocalDateTime.now())
                .update();
        if (!ok) {
            // 并发下已被隐藏：目标状态已达成，按幂等 200 处理
            log.debug("[R1] 订单隐藏 CAS 未命中（并发已隐藏）, orderId={}", orderId);
        }
    }

    /* ==================================================================== */
    /* ==================== 管理端：CI-26 / CN-33 / 发货 / 关单 ==================== */
    /* ==================================================================== */

    @Override
    public PageVO<OrderAdminDTO> adminListOrders(Integer pageNo, Integer pageSize, String status,
                                                 String aftersaleStatus, String orderSn, String keyword,
                                                 Long userId, String addTimeMin, String addTimeMax,
                                                 String sortBy, Boolean isAsc) {
        int pn = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int ps = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);

        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getDeleted, false);
        if (StrUtil.isNotBlank(status)) {
            List<Integer> codes = OrderStatus.parseMulti(status);
            if (codes == null) {
                throw new BadRequestException("status 取值非法", BadRequestException.PARAM_INVALID);
            }
            if (!codes.isEmpty()) {
                qw.in(Order::getOrderStatus, codes);
            }
        }
        if (StrUtil.isNotBlank(aftersaleStatus)) {
            List<Integer> codes = parseAftersaleMulti(aftersaleStatus);
            if (codes == null) {
                throw new BadRequestException("aftersaleStatus 取值非法", BadRequestException.PARAM_INVALID);
            }
            if (!codes.isEmpty()) {
                String inClause = codes.stream().map(String::valueOf).collect(Collectors.joining(", "));
                qw.exists("SELECT 1 FROM litemall_aftersale a WHERE a.order_id = litemall_order.id "
                        + "AND a.deleted = 0 AND a.status IN (" + inClause + ")");
            }
        }
        if (StrUtil.isNotBlank(orderSn)) {
            qw.eq(Order::getOrderSn, orderSn.trim());
        }
        String kw = StrUtil.trimToEmpty(keyword);
        if (!kw.isEmpty()) {
            if (kw.length() > 64) {
                throw new BadRequestException("keyword 长度不能超过 64", BadRequestException.PARAM_INVALID);
            }
            qw.and(w -> w.like(Order::getOrderSn, kw).or().like(Order::getConsignee, kw)
                    .or().like(Order::getMobile, kw));
        }
        if (userId != null) {
            qw.eq(Order::getUserId, userId);
        }
        LocalDateTime min = parseTime(addTimeMin, "addTimeMin");
        if (min != null) {
            qw.ge(Order::getAddTime, min);
        }
        LocalDateTime max = parseTime(addTimeMax, "addTimeMax");
        if (max != null) {
            qw.le(Order::getAddTime, max);
        }

        boolean asc = Boolean.TRUE.equals(isAsc);
        if ("orderAmount".equalsIgnoreCase(StrUtil.trimToEmpty(sortBy))) {
            qw.orderBy(true, asc, Order::getOrderPrice);
        } else {
            qw.orderBy(true, asc, Order::getAddTime);
        }
        qw.orderBy(true, asc, Order::getId);

        Page<Order> page = page(new Page<>(pn, ps), qw);
        List<Order> records = page.getRecords();
        Map<Long, List<OrderGoods>> goodsMap = loadOrderGoods(records);
        Map<Long, Aftersale> aftersaleMap = loadLatestUnfinishedAftersale(
                records.stream().map(Order::getId).collect(Collectors.toList()));
        List<OrderAdminDTO> rows = records.stream().map(o -> {
            List<OrderGoods> lines = goodsMap.getOrDefault(o.getId(), List.of());
            OrderAdminDTO row = new OrderAdminDTO();
            row.setId(o.getId());
            row.setOrderSn(o.getOrderSn());
            row.setUserId(o.getUserId());
            row.setOrderAmount(Money.fen(o.getOrderPrice()));
            row.setStatus(OrderStatus.ident(o.getOrderStatus()));
            row.setStatusText(OrderStatus.label(o.getOrderStatus()));
            row.setFullyRefunded(OrderRules.isFullyRefunded(lines));
            row.setRefundedAmount(Money.fen(OrderRules.refundedAmount(lines)));
            Aftersale a = aftersaleMap.get(o.getId());
            row.setAftersaleStatus(a == null ? null : AftersaleStatus.ident(a.getStatus()));
            row.setAftersaleStatusText(a == null ? null : AftersaleStatus.label(a.getStatus()));
            row.setGoodsSummary(goodsSummary(lines));
            row.setAddTime(o.getAddTime());
            return row;
        }).collect(Collectors.toList());
        return PageVO.of(page, rows);
    }

    @Override
    public OrderAdminDetailDTO adminOrderDetail(Long id) {
        Order order = orderMapper().selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, id)
                .eq(Order::getDeleted, false));
        if (order == null) {
            throw new NotFoundException("订单不存在", CODE_ORDER_NOT_FOUND);
        }
        List<OrderGoods> lines = listGoods(order.getId());
        List<Aftersale> unfinished = listUnfinishedAftersale(order.getId());
        Map<Long, Aftersale> byLine = unfinished.stream()
                .filter(a -> a.getOrderGoodsId() != null)
                .collect(Collectors.toMap(Aftersale::getOrderGoodsId, a -> a, (a, b) -> a));

        OrderAdminDetailDTO dto = new OrderAdminDetailDTO();
        dto.setId(order.getId());
        dto.setOrderSn(order.getOrderSn());
        dto.setUserId(order.getUserId());
        dto.setUserDeleted(Boolean.TRUE.equals(order.getUserDeleted()));
        dto.setStatus(OrderStatus.ident(order.getOrderStatus()));
        dto.setStatusText(OrderStatus.label(order.getOrderStatus()));
        dto.setConsignee(order.getConsignee());
        dto.setMobile(order.getMobile());
        dto.setAddress(order.getAddress());
        dto.setMessage(order.getMessage());
        dto.setGoodsAmount(Money.fen(order.getGoodsPrice()));
        dto.setFreightAmount(Money.fen(order.getFreightPrice()));
        dto.setCouponAmount(Money.fen(order.getCouponPrice()));
        dto.setOrderAmount(Money.fen(order.getOrderPrice()));
        dto.setActualPrice(Money.fen(order.getActualPrice()));
        dto.setPayTime(format(order.getPayTime()));
        dto.setExpireTime(Objects.equals(order.getOrderStatus(), OrderStatus.UNPAID)
                ? format(order.getExpireTime()) : null);
        dto.setShipChannel(order.getShipChannel());
        dto.setShipSn(order.getShipSn());
        dto.setShipTime(format(order.getShipTime()));
        int shipUpdateCount = shipUpdateCount(order.getId());
        dto.setShipUpdateCount(shipUpdateCount);
        dto.setCanUpdateShip(Objects.equals(order.getOrderStatus(), OrderStatus.SHIPPED) && shipUpdateCount == 0);
        dto.setCloseType(order.getCloseType());
        dto.setCloseTypeText(OrderStatus.closeTypeLabel(order.getCloseType()));
        dto.setFullyRefunded(OrderRules.isFullyRefunded(lines));
        dto.setRefundedAmount(Money.fen(OrderRules.refundedAmount(lines)));
        dto.setAddTime(format(order.getAddTime()));

        List<OrderAdminDetailDTO.Line> lineDTOs = new ArrayList<>(lines.size());
        for (OrderGoods og : lines) {
            Aftersale a = byLine.get(og.getId());
            OrderAdminDetailDTO.Line line = new OrderAdminDetailDTO.Line();
            line.setOrderGoodsId(og.getId());
            line.setProductId(og.getProductId());
            line.setGoodsId(og.getGoodsId());
            line.setGoodsName(og.getGoodsName());
            line.setSpecText(specText(og.getSpecifications()));
            line.setPrice(Money.fen(og.getPrice()));
            line.setNumber(og.getNumber());
            line.setActualAmount(Money.fen(og.getActualAmount()));
            line.setRefundedNumber(og.getRefundedNumber());
            line.setRefundStatus(og.getRefundStatus());
            line.setRefundStatusText(AftersaleStatus.refundStatusLabel(og.getRefundStatus()));
            line.setAftersaleId(a == null ? null : a.getId());
            line.setAftersaleStatus(a == null ? null : AftersaleStatus.ident(a.getStatus()));
            line.setAftersaleStatusText(a == null ? null : AftersaleStatus.label(a.getStatus()));
            lineDTOs.add(line);
        }
        dto.setLines(lineDTOs);
        return dto;
    }

    @Override
    public OrderAdminStatsDTO adminOrderStats() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        long todayOrders = count(new LambdaQueryWrapper<Order>()
                .eq(Order::getDeleted, false)
                .ge(Order::getAddTime, today));
        long undelivered = count(new LambdaQueryWrapper<Order>()
                .eq(Order::getDeleted, false)
                .eq(Order::getOrderStatus, OrderStatus.UNDELIVERED));
        OrderAdminStatsDTO stats = new OrderAdminStatsDTO();
        stats.setTodayOrders(todayOrders);
        stats.setUndelivered(undelivered);
        return stats;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminShip(OrderShipDTO dto) {
        Order order = requireOrder(dto.getOrderId());
        String channel = requireShipField(dto.getShipChannel(), "快递公司");
        String sn = requireShipField(dto.getShipSn(), "快递单号");
        LocalDateTime now = LocalDateTime.now();
        boolean ok = lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getOrderStatus, OrderStatus.UNDELIVERED)
                .eq(Order::getDeleted, false)
                .set(Order::getOrderStatus, OrderStatus.SHIPPED)
                .set(Order::getShipChannel, channel)
                .set(Order::getShipSn, sn)
                .set(Order::getShipTime, now)
                .set(Order::getUpdateTime, now)
                .update();
        if (!ok) {
            throw new ConflictException("只有待发货订单可以发货", CODE_ORDER_STATUS_ILLEGAL);
        }
    }

    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public void adminClose(OrderCloseDTO dto) {
        Order order = requireOrder(dto.getOrderId());
        String reason = StrUtil.trimToNull(dto.getReason());
        if (reason != null && reason.length() > 255) {
            throw new BadRequestException("关单原因不能超过 255 字", BadRequestException.PARAM_INVALID);
        }
        closeUnpaidOrder(order, OrderStatus.CLOSE_ADMIN, dto.getAdminId(), LOG_CLOSE, reason,
                "该订单当前状态不可关闭");
    }

    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public CloseExpiredResultDTO adminCloseExpired(Integer limit, Boolean dryRun) {
        int max = limit == null ? 100 : limit;
        if (max < 1 || max > 200) {
            throw new BadRequestException("limit 取值范围 1–200", BadRequestException.PARAM_INVALID);
        }
        boolean scanOnly = Boolean.TRUE.equals(dryRun);
        LocalDateTime now = LocalDateTime.now();
        List<Order> expired = orderMapper().selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getDeleted, false)
                .eq(Order::getOrderStatus, OrderStatus.UNPAID)
                .le(Order::getExpireTime, now)
                .orderByAsc(Order::getId)
                .last("LIMIT " + max));

        CloseExpiredResultDTO result = new CloseExpiredResultDTO();
        result.setTotal(expired.size());
        int succeeded = 0;
        int failed = 0;
        for (Order order : expired) {
            CloseExpiredResultDTO.Item item = new CloseExpiredResultDTO.Item();
            item.setId(order.getId());
            if (scanOnly) {
                item.setOk(true);
                item.setReason("dryRun：未落库");
                succeeded++;
                result.getItems().add(item);
                continue;
            }
            try {
                closeUnpaidOrder(order, OrderStatus.CLOSE_TIMEOUT, null, LOG_CLOSE_EXPIRED, null,
                        "已被用户取消（状态已变更）");
                item.setOk(true);
                succeeded++;
            } catch (ConflictException e) {
                item.setOk(false);
                item.setReason(e.getMessage());
                failed++;
            }
            result.getItems().add(item);
        }
        result.setSucceeded(succeeded);
        result.setFailed(failed);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminUpdateShip(OrderShipDTO dto) {
        Order order = requireOrder(dto.getOrderId());
        String channel = requireShipField(dto.getShipChannel(), "快递公司");
        String sn = requireShipField(dto.getShipSn(), "快递单号");
        String reason = StrUtil.trimToNull(dto.getReason());
        if (reason != null && reason.length() > 255) {
            throw new BadRequestException("修改原因不能超过 255 字", BadRequestException.PARAM_INVALID);
        }
        if (!Objects.equals(order.getOrderStatus(), OrderStatus.SHIPPED)) {
            throw new ConflictException("只有已发货订单可以修改物流单号", CODE_ORDER_STATUS_ILLEGAL);
        }
        if (shipUpdateCount(order.getId()) > 0) {
            throw new ConflictException("物流单号已修改过，不能再次修改", CODE_ORDER_STATUS_ILLEGAL);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean ok = lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getOrderStatus, OrderStatus.SHIPPED)
                .eq(Order::getDeleted, false)
                .set(Order::getShipChannel, channel)
                .set(Order::getShipSn, sn)
                .set(Order::getUpdateTime, now)
                .update();
        if (!ok) {
            throw new ConflictException("只有已发货订单可以修改物流单号", CODE_ORDER_STATUS_ILLEGAL);
        }
        String oldValue = StrUtil.nullToEmpty(order.getShipChannel()) + " / " + StrUtil.nullToEmpty(order.getShipSn());
        writeOrderLog(LOG_SHIP_UPDATE, order.getId(), null,
                oldValue + " → " + channel + " / " + sn, reason);
    }

    /* ==================================================================== */
    /* ==================== 对内：CI-97 / CN-IN-01 / CN-IN-04 ==================== */
    /* ==================================================================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaySuccessResultDTO markPaid(PaySuccessDTO dto) {
        PaySuccessResultDTO result = new PaySuccessResultDTO();
        Order order = orderMapper().selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, dto.getOrderId())
                .eq(Order::getDeleted, false));
        if (order == null) {
            throw new NotFoundException("订单不存在", CODE_ORDER_NOT_FOUND);
        }
        // 幂等闸门：pay_time IS NOT NULL 视为已处理，不再动库存（同单只实扣一次）
        if (order.getPayTime() != null) {
            result.setApplied(false);
            result.setDeducted(false);
            return result;
        }
        if (Objects.equals(order.getOrderStatus(), OrderStatus.CANCELED)) {
            // DR-11：已关闭订单不扣库存；自动退款由 pay 域发起（资金走 pay，R1-04 接线）
            log.error("[DR-11] 已关闭订单收到支付成功回调，需人工退款。orderId={}, payId={}",
                    order.getId(), dto.getPayId());
            result.setApplied(false);
            result.setDeducted(false);
            return result;
        }
        if (!Objects.equals(order.getOrderStatus(), OrderStatus.UNPAID)) {
            log.error("[CI-97] 非未付款订单收到支付成功回调，忽略。orderId={}, status={}",
                    order.getId(), order.getOrderStatus());
            result.setApplied(false);
            result.setDeducted(false);
            return result;
        }
        List<OrderGoods> lines = listGoods(order.getId());
        List<PaySuccessResultDTO.Shortage> shortage = checkStockForPay(lines);
        if (!shortage.isEmpty()) {
            // P-31：库存不足 → 200 + deducted=false + shortage，订单保持 101，不自动退款
            log.error("[CI-97] 实扣前校验库存不足，订单保持未付款。orderId={}, shortage={}",
                    order.getId(), shortage.size());
            result.setApplied(false);
            result.setDeducted(false);
            result.setShortage(shortage);
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        boolean ok = lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getOrderStatus, OrderStatus.UNPAID)
                .eq(Order::getDeleted, false)
                .set(Order::getOrderStatus, OrderStatus.UNDELIVERED)
                .set(Order::getPayId, dto.getPayId())
                .set(Order::getPayTime, now)
                .set(Order::getUpdateTime, now)
                .update();
        if (!ok) {
            result.setApplied(false);
            result.setDeducted(false);
            return result;
        }

        try {
            DeductBatchResultDTO deduct = goodsClient.deductStockBatch(stockChangeOf(lines, order.getId()));
            if (deduct != null && !deduct.isDeducted()) {
                log.error("[CI-97] 库存实扣失败（订单已置待发货，需人工核对库存）。orderId={}, items={}",
                        order.getId(), deduct.getItems() == null ? 0 : deduct.getItems().size());
                result.setDeducted(false);
                if (deduct.getItems() != null) {
                    for (DeductBatchResultDTO.Item item : deduct.getItems()) {
                        if (item.isDeducted() || item.getShortage() == null) {
                            continue;
                        }
                        PaySuccessResultDTO.Shortage s = new PaySuccessResultDTO.Shortage();
                        s.setProductId(item.getProductId());
                        s.setNeed(item.getShortage());
                        result.getShortage().add(s);
                    }
                }
            } else {
                result.setDeducted(true);
            }
        } catch (Exception e) {
            log.error("[CI-97] 库存实扣异常（订单已置待发货，需人工核对库存）。orderId={}", order.getId(), e);
            result.setDeducted(false);
        }
        result.setApplied(true);
        return result;
    }

    @Override
    public OrderGoodsStatusDTO orderGoodsStatus(Long id, Long userId) {
        OrderGoods og = orderGoodsMapper.selectOne(new LambdaQueryWrapper<OrderGoods>()
                .eq(OrderGoods::getId, id)
                .eq(OrderGoods::getDeleted, false));
        if (og == null) {
            throw new NotFoundException("订单商品不存在", CODE_RESOURCE_NOT_FOUND);
        }
        Order order = orderMapper().selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, og.getOrderId())
                .eq(Order::getDeleted, false));
        if (order == null || (userId != null && !Objects.equals(userId, order.getUserId()))) {
            throw new NotFoundException("订单商品不存在", CODE_RESOURCE_NOT_FOUND);
        }
        Integer comment = og.getComment();
        boolean commented = comment != null && comment > 0;
        boolean refunded = Objects.equals(og.getRefundStatus(), 2);
        boolean statusAllowed = Objects.equals(order.getOrderStatus(), OrderStatus.CONFIRMED)
                || Objects.equals(order.getOrderStatus(), OrderStatus.REFUND);

        OrderGoodsStatusDTO dto = new OrderGoodsStatusDTO();
        dto.setOrderGoodsId(og.getId());
        dto.setOrderId(order.getId());
        dto.setUserId(order.getUserId());
        dto.setOrderStatus(order.getOrderStatus());
        dto.setOrderStatusText(OrderStatus.label(order.getOrderStatus()));
        dto.setRefundStatus(og.getRefundStatus());
        dto.setRefundStatusText(AftersaleStatus.refundStatusLabel(og.getRefundStatus()));
        dto.setRefundedNumber(og.getRefundedNumber());
        dto.setNumber(og.getNumber());
        dto.setCommented(commented);
        dto.setCommentId(commented ? Long.valueOf(comment) : null);
        dto.setCanComment(statusAllowed && !refunded && !commented);
        return dto;
    }

    @Override
    public LockedStockDTO lockedStock(String productIds) {
        List<Long> ids = new ArrayList<>();
        if (StrUtil.isNotBlank(productIds)) {
            String[] parts = productIds.split(",");
            if (parts.length > 200) {
                throw new BadRequestException("productIds 数量不能超过 200", BadRequestException.PARAM_INVALID);
            }
            for (String raw : parts) {
                String item = StrUtil.trimToEmpty(raw);
                if (item.isEmpty() || !item.chars().allMatch(Character::isDigit)) {
                    throw new BadRequestException("productIds 格式非法", BadRequestException.PARAM_INVALID);
                }
                long pid;
                try {
                    pid = Long.parseLong(item);
                } catch (NumberFormatException e) {
                    throw new BadRequestException("productIds 格式非法", BadRequestException.PARAM_INVALID);
                }
                if (ids.contains(pid)) {
                    throw new BadRequestException("productIds 不能重复", BadRequestException.PARAM_INVALID);
                }
                ids.add(pid);
            }
        }
        LockedStockDTO dto = new LockedStockDTO();
        List<LockedStockDTO.Item> rows = orderMapper().selectLockedStock(ids.isEmpty() ? null : ids);
        dto.setList(rows == null ? new ArrayList<>() : rows);
        return dto;
    }

    /* ==================================================================== */
    /* ==================== 共享：关单 + 释放（CI-85 / CN-10 / CN-11） ==================== */
    /* ==================================================================== */

    /**
     * 未付款订单关闭的唯一实现（CI-85 / CN-10 / CN-11 共用，BR-ORDER-13）。
     * <p>
     * 顺序：CAS {@code 101 → 103}（成功才继续）→ 释放锁量与券 → 留痕。
     * CAS 失败说明并发已被关闭/支付/发货，直接抛 409（由调用方决定文案）。
     * </p>
     */
    private void closeUnpaidOrder(Order order, int closeType, Long adminId, String logAction,
                                  String reason, String conflictMessage) {
        LocalDateTime now = LocalDateTime.now();
        boolean ok = lambdaUpdate()
                .eq(Order::getId, order.getId())
                .eq(Order::getOrderStatus, OrderStatus.UNPAID)
                .eq(Order::getDeleted, false)
                .set(Order::getOrderStatus, OrderStatus.CANCELED)
                .set(Order::getCloseType, closeType)
                .set(Order::getEndTime, now)
                .set(Order::getUpdateTime, now)
                .update();
        if (!ok) {
            throw new ConflictException(conflictMessage, CODE_ORDER_STATUS_ILLEGAL);
        }
        releaseOrderResources(order);
        if (logAction != null) {
            writeOrderLog(logAction, order.getId(), adminId, OrderStatus.closeTypeLabel(closeType), reason);
        }
    }

    /**
     * 释放订单占用的资源。
     * <ul>
     *   <li>未支付过半（{@code pay_time == null}）：只回退优惠券——库存从未实扣，无需回补。</li>
     *   <li>已支付（售后退款等场景）：额外回补库存。</li>
     * </ul>
     */
    private void releaseOrderResources(Order order) {
        if (order.getCouponPrice() != null && order.getCouponPrice().signum() > 0) {
            try {
                CouponUseDTO rollback = new CouponUseDTO();
                rollback.setOrderId(order.getId());
                marketingClient.rollbackCoupon(rollback);
            } catch (Exception e) {
                // 券回退失败不能阻断关单（订单已关，券超时由 marketing 侧补偿），但必须留痕告警
                log.error("[R1] 关单回退优惠券失败，需人工核对。orderId={}", order.getId(), e);
            }
        }
        if (order.getPayTime() != null) {
            List<OrderGoods> lines = listGoods(order.getId());
            if (!lines.isEmpty()) {
                try {
                    goodsClient.releaseStockBatch(stockChangeOf(lines, order.getId()));
                } catch (Exception e) {
                    log.error("[R1] 关单回补库存失败，需人工核对。orderId={}", order.getId(), e);
                }
            }
        }
    }

    /* ==================================================================== */
    /* ==================== 私有工具 ==================== */
    /* ==================================================================== */

    private OrderMapper orderMapper() {
        return getBaseMapper();
    }

    private Long requireUser() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        return userId;
    }

    /** 本人订单（不存在或非本人一律 404，不泄露存在性） */
    private Order requireOwnOrder(Long orderId, Long userId) {
        if (orderId == null) {
            throw new BadRequestException("订单ID不能为空", BadRequestException.PARAM_INVALID);
        }
        Order order = orderMapper().selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getUserId, userId)
                .eq(Order::getDeleted, false));
        if (order == null) {
            throw new NotFoundException("订单不存在", CODE_ORDER_NOT_FOUND);
        }
        return order;
    }

    /** 管理端/对内订单（不校验归属） */
    private Order requireOrder(Long orderId) {
        if (orderId == null) {
            throw new BadRequestException("订单ID不能为空", BadRequestException.PARAM_INVALID);
        }
        Order order = orderMapper().selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getDeleted, false));
        if (order == null) {
            throw new NotFoundException("订单不存在", CODE_ORDER_NOT_FOUND);
        }
        return order;
    }

    private AddressInfoDTO requireAddress(Long addressId) {
        if (addressId == null) {
            throw new BadRequestException("收货地址不能为空", CODE_ADDRESS_INVALID);
        }
        AddressInfoDTO address = userClient.getAddressById(addressId);
        if (address == null) {
            // P-09：地址已删 → 400 ADDRESS_INVALID；归属校验依赖 user 内对接口（当前未回传 userId）
            throw new BadRequestException("收货地址不存在", CODE_ADDRESS_INVALID);
        }
        return address;
    }

    /**
     * 解析下单行：{@code cartIds} 与 {@code buyNow} 二选一。
     * <p>价格/规格一律以 goods 当前货品为准（购物车里的价格可能已过期）。</p>
     */
    private List<DraftLine> resolveDrafts(Long userId, List<Long> cartIds, OrderConfirmDTO.BuyNow buyNow) {
        boolean hasCart = cartIds != null && !cartIds.isEmpty();
        boolean hasBuyNow = buyNow != null;
        if (hasCart == hasBuyNow) {
            throw new BadRequestException("cartIds 与 buyNow 必须二选一", BadRequestException.PARAM_INVALID);
        }
        return hasCart ? resolveCartDrafts(userId, cartIds) : resolveBuyNowDraft(buyNow);
    }

    private List<DraftLine> resolveCartDrafts(Long userId, List<Long> cartIds) {
        List<Long> ids = distinct(cartIds);
        List<Cart> carts = cartMapper.selectByIds(ids);
        Map<Long, Cart> byId = new HashMap<>();
        for (Cart cart : carts) {
            if (cart.getDeleted() == null || !cart.getDeleted()) {
                byId.put(cart.getId(), cart);
            }
        }
        if (byId.size() != ids.size()) {
            throw new BadRequestException("购物车项不存在或已失效", BadRequestException.PARAM_INVALID);
        }
        List<DraftLine> drafts = new ArrayList<>(ids.size());
        for (Long cartId : ids) {
            Cart cart = byId.get(cartId);
            if (!Objects.equals(cart.getUserId(), userId)) {
                throw new BadRequestException("购物车项不存在或不属于当前用户", BadRequestException.PARAM_INVALID);
            }
            GoodsProductInfoDTO product = goodsClient.getProductInfo(cart.getProductId());
            if (product == null) {
                throw new NotFoundException("商品规格不存在或已下架", CODE_RESOURCE_NOT_FOUND);
            }
            drafts.add(toDraft(product, cart.getNumber(), cart));
        }
        return drafts;
    }

    private List<DraftLine> resolveBuyNowDraft(OrderConfirmDTO.BuyNow buyNow) {
        if (buyNow.getProductId() == null || buyNow.getNumber() == null || buyNow.getNumber() < 1) {
            throw new BadRequestException("buyNow.productId 与 buyNow.number 必填且 number ≥ 1",
                    BadRequestException.PARAM_INVALID);
        }
        GoodsProductInfoDTO product = goodsClient.getProductInfo(buyNow.getProductId());
        if (product == null) {
            throw new NotFoundException("商品规格不存在或已下架", CODE_RESOURCE_NOT_FOUND);
        }
        return List.of(toDraft(product, buyNow.getNumber(), null));
    }

    private DraftLine toDraft(GoodsProductInfoDTO product, Integer number, Cart cart) {
        int qty = number == null || number < 1 ? 0 : number;
        if (qty == 0) {
            throw new BadRequestException("购买数量必须大于 0", BadRequestException.PARAM_INVALID);
        }
        String specs = StrUtil.isNotBlank(product.getSpecifications())
                ? product.getSpecifications()
                : (cart == null ? null : cart.getSpecifications());
        String skuCode = StrUtil.nullToEmpty(product.getSkuCode());
        String specValueIds = product.getSpecValueIds() == null || product.getSpecValueIds().isEmpty()
                ? "" : String.join(",", product.getSpecValueIds());
        BigDecimal price = product.getPrice() != null
                ? product.getPrice()
                : (cart == null || cart.getPrice() == null ? BigDecimal.ZERO : cart.getPrice());
        return new DraftLine(
                product.getId() != null ? product.getId() : (cart == null ? null : cart.getProductId()),
                product.getGoodsId() != null ? product.getGoodsId() : (cart == null ? null : cart.getGoodsId()),
                StrUtil.isNotBlank(product.getGoodsName()) ? product.getGoodsName() : (cart == null ? null : cart.getGoodsName()),
                product.getGoodsSn() != null ? product.getGoodsSn() : (cart == null ? null : cart.getGoodsSn()),
                price,
                qty,
                specs,
                product.getPicUrl() != null ? product.getPicUrl() : (cart == null ? null : cart.getPicUrl()),
                skuCode,
                specValueIds,
                product.getSellable());
    }

    /** 提交前的可售校验：任一不足 → 整单 409（不生成订单、不锁任何行） */
    private void assertSellable(List<DraftLine> drafts) {
        for (DraftLine d : drafts) {
            if (d.sellable() != null && d.sellable() < d.number()) {
                throw new ConflictException(d.specText() + " 仅剩 " + d.sellable() + " 件", CODE_STOCK_NOT_ENOUGH);
            }
        }
    }

    /** 支付成功前的库存校验（返回缺口列表；空表示可扣） */
    private List<PaySuccessResultDTO.Shortage> checkStockForPay(List<OrderGoods> lines) {
        List<PaySuccessResultDTO.Shortage> shortage = new ArrayList<>();
        for (OrderGoods og : lines) {
            GoodsProductInfoDTO product;
            try {
                product = goodsClient.getProductInfo(og.getProductId());
            } catch (Exception e) {
                log.warn("[CI-97] 查询货品失败，跳过该行库存校验。productId={}", og.getProductId(), e);
                continue;
            }
            if (product == null) {
                continue;
            }
            Integer available = product.getNumber() != null ? product.getNumber() : product.getSellable();
            int need = og.getNumber() == null ? 0 : og.getNumber();
            if (available != null && available < need) {
                PaySuccessResultDTO.Shortage s = new PaySuccessResultDTO.Shortage();
                s.setProductId(og.getProductId());
                s.setNeed(need);
                s.setAvailable(available);
                shortage.add(s);
            }
        }
        return shortage;
    }

    private BigDecimal sumAmount(List<DraftLine> drafts) {
        BigDecimal total = BigDecimal.ZERO;
        for (DraftLine d : drafts) {
            total = total.add(d.price().multiply(BigDecimal.valueOf(d.number())));
        }
        return total;
    }

    private List<BigDecimal> lineAmounts(List<DraftLine> drafts) {
        return drafts.stream()
                .map(d -> d.price().multiply(BigDecimal.valueOf(d.number())))
                .collect(Collectors.toList());
    }

    /** 券额试算：不可用 → 400 COUPON_NOT_USABLE；券额钳制在 [0, goodsAmount] */
    private BigDecimal resolveCouponAmount(Long userId, Long couponId, BigDecimal goodsAmount) {
        if (couponId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal coupon;
        try {
            CouponCheckDTO check = new CouponCheckDTO();
            check.setCouponUserId(couponId);
            check.setGoodsAmount(goodsAmount);
            coupon = marketingClient.checkCoupon(check);
        } catch (Exception e) {
            log.warn("[CI-80] 优惠券校验失败, userId={}, couponId={}", userId, couponId, e);
            throw new BadRequestException("优惠券不可用", CODE_COUPON_NOT_USABLE);
        }
        if (coupon == null || coupon.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return coupon.min(goodsAmount);
    }

    private List<OrderGoods> listGoods(Long orderId) {
        return orderGoodsMapper.selectList(new LambdaQueryWrapper<OrderGoods>()
                .eq(OrderGoods::getOrderId, orderId)
                .eq(OrderGoods::getDeleted, false));
    }

    private List<Aftersale> listUnfinishedAftersale(Long orderId) {
        return aftersaleMapper.selectList(new LambdaQueryWrapper<Aftersale>()
                .eq(Aftersale::getOrderId, orderId)
                .eq(Aftersale::getDeleted, false)
                .in(Aftersale::getStatus, toIntegerList(AftersaleStatus.UNFINISHED))
                .orderByDesc(Aftersale::getId));
    }

    private long countUnfinishedAftersale(Long orderId) {
        return aftersaleMapper.selectCount(new LambdaQueryWrapper<Aftersale>()
                .eq(Aftersale::getOrderId, orderId)
                .eq(Aftersale::getDeleted, false)
                .in(Aftersale::getStatus, toIntegerList(AftersaleStatus.UNFINISHED)));
    }

    private List<Integer> toIntegerList(int[] values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int v : values) {
            list.add(v);
        }
        return list;
    }

    private Map<Long, Aftersale> loadLatestUnfinishedAftersale(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        List<Aftersale> list = aftersaleMapper.selectList(new LambdaQueryWrapper<Aftersale>()
                .in(Aftersale::getOrderId, orderIds)
                .eq(Aftersale::getDeleted, false)
                .in(Aftersale::getStatus, toIntegerList(AftersaleStatus.UNFINISHED))
                .orderByDesc(Aftersale::getId));
        Map<Long, Aftersale> map = new LinkedHashMap<>();
        for (Aftersale a : list) {
            map.putIfAbsent(a.getOrderId(), a);
        }
        return map;
    }

    private Aftersale latestUnfinished(List<Aftersale> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    private OrderAftersaleBriefVO briefOf(Aftersale a) {
        if (a == null) {
            return null;
        }
        OrderAftersaleBriefVO vo = new OrderAftersaleBriefVO();
        vo.setAftersaleId(a.getId());
        vo.setAftersaleSn(a.getAftersaleSn());
        vo.setStatus(a.getStatus());
        vo.setStatusText(AftersaleStatus.label(a.getStatus()));
        vo.setType(a.getType());
        vo.setTypeText(AftersaleStatus.typeLabel(a.getType()));
        return vo;
    }

    private List<OrderDetailLineVO> buildDetailLines(Order order, List<OrderGoods> lines,
                                                     List<Aftersale> unfinished) {
        Map<Long, Integer> unfinishedQty = new HashMap<>();
        for (Aftersale a : unfinished) {
            if (a.getOrderGoodsId() == null) {
                continue;
            }
            int qty = a.getNumber() == null ? 0 : a.getNumber();
            unfinishedQty.merge(a.getOrderGoodsId(), qty, Integer::sum);
        }
        List<OrderDetailLineVO> list = new ArrayList<>(lines.size());
        for (OrderGoods og : lines) {
            OrderDetailLineVO vo = new OrderDetailLineVO();
            vo.setOrderGoodsId(og.getId());
            vo.setProductId(og.getProductId());
            vo.setGoodsName(og.getGoodsName());
            vo.setSpecText(specText(og.getSpecifications()));
            vo.setPrice(Money.fen(og.getPrice()));
            vo.setNumber(og.getNumber());
            vo.setActualAmount(Money.fen(og.getActualAmount()));
            vo.setCouponShare(Money.fen(og.getCouponShare()));
            vo.setRefundedNumber(og.getRefundedNumber());
            vo.setRefundStatus(og.getRefundStatus());
            vo.setRefundStatusText(AftersaleStatus.refundStatusLabel(og.getRefundStatus()));
            vo.setCanApply(canApply(order, og, unfinishedQty.getOrDefault(og.getId(), 0)));
            list.add(vo);
        }
        return list;
    }

    /**
     * 行级售后可申请信息（CI-84 canApply；实时计算、不缓存）。
     * <p>
     * 规则（与 07 §4.1 CI-91 的判定顺序一致，售后侧以同一口径做二次校验）：
     * 订单必须已支付且未关闭；行未全额退款；行剩余可申请件数 &gt; 0；301 需在 confirm_time+7 天内。
     * 仅退款(1) 在待发货即可申请；退货退款(2) 需已发货/已收货。
     * </p>
     */
    private OrderDetailLineVO.CanApply canApply(Order order, OrderGoods og, int unfinishedQty) {
        OrderDetailLineVO.CanApply can = new OrderDetailLineVO.CanApply();
        can.setTypes(new ArrayList<>());
        can.setMaxQty(0);
        Integer status = order.getOrderStatus();
        if (Objects.equals(status, OrderStatus.UNPAID)) {
            can.setReason("订单未支付");
            return can;
        }
        if (Objects.equals(status, OrderStatus.CANCELED)) {
            can.setReason("订单已关闭");
            return can;
        }
        if (Objects.equals(status, OrderStatus.COMPLETED)) {
            can.setReason("订单已完成");
            return can;
        }
        if (Objects.equals(status, OrderStatus.REFUND) || Objects.equals(status, OrderStatus.REFUND_DONE)) {
            can.setReason("订单退款中");
            return can;
        }
        if (Objects.equals(og.getRefundStatus(), 2)) {
            can.setReason("该行已全额退款");
            return can;
        }
        int number = og.getNumber() == null ? 0 : og.getNumber();
        int refunded = og.getRefundedNumber() == null ? 0 : og.getRefundedNumber();
        int remain = number - refunded - unfinishedQty;
        if (remain <= 0) {
            can.setReason(unfinishedQty > 0 ? "该行退款中" : "该行已无可申请件数");
            return can;
        }
        if (Objects.equals(status, OrderStatus.CONFIRMED) && order.getConfirmTime() != null
                && order.getConfirmTime().plusDays(AFTERSALE_WINDOW_DAYS).isBefore(LocalDateTime.now())) {
            can.setReason("已超 " + AFTERSALE_WINDOW_DAYS + " 天");
            return can;
        }
        List<Integer> types = new ArrayList<>();
        types.add(AftersaleStatus.TYPE_REFUND_ONLY);
        if (Objects.equals(status, OrderStatus.SHIPPED) || Objects.equals(status, OrderStatus.CONFIRMED)) {
            types.add(AftersaleStatus.TYPE_RETURN_GOODS);
        }
        can.setTypes(types);
        can.setMaxQty(remain);
        return can;
    }

    private List<OrderTimelineVO> buildTimeline(Order order) {
        List<OrderTimelineVO> list = new ArrayList<>();
        addTimeline(list, order.getAddTime(), "user", "用户", "提交订单");
        addTimeline(list, order.getPayTime(), "user", "用户", "支付成功");
        addTimeline(list, order.getShipTime(), "admin", "管理员", "已发货");
        addTimeline(list, order.getConfirmTime(), "user", "用户", "确认收货");
        String closeAction = order.getCloseType() == null
                ? "订单关闭"
                : "订单关闭（" + OrderStatus.closeTypeLabel(order.getCloseType()) + "）";
        addTimeline(list, order.getEndTime(), "system", "系统", closeAction);
        list.sort(Comparator.comparing(OrderTimelineVO::getTime));
        return list;
    }

    private void addTimeline(List<OrderTimelineVO> list, LocalDateTime time,
                             String actor, String actorText, String action) {
        if (time == null) {
            return;
        }
        OrderTimelineVO vo = new OrderTimelineVO();
        vo.setTime(time.format(DT));
        vo.setActor(actor);
        vo.setActorText(actorText);
        vo.setAction(action);
        list.add(vo);
    }

    private int shipUpdateCount(Long orderId) {
        Long count = orderLogMapper.selectCount(new LambdaQueryWrapper<OrderLog>()
                .eq(OrderLog::getTargetType, LOG_TARGET_TYPE)
                .eq(OrderLog::getTargetId, orderId)
                .eq(OrderLog::getAction, LOG_SHIP_UPDATE)
                .eq(OrderLog::getDeleted, false));
        return count == null ? 0 : count.intValue();
    }

    /** 运营动作留痕（litemall_log；admin 列存 adminId 字符串，缺失记 null，禁止伪造 0） */
    private void writeOrderLog(String action, Long orderId, Long adminId, String result, String comment) {
        LocalDateTime now = LocalDateTime.now();
        OrderLog logRow = new OrderLog()
                .setAction(action)
                .setTargetType(LOG_TARGET_TYPE)
                .setTargetId(orderId)
                .setAdmin(adminId == null ? null : String.valueOf(adminId))
                .setStatus(1)
                .setResult(StrUtil.maxLength(result, 120))
                .setComment(StrUtil.maxLength(comment, 250))
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        try {
            orderLogMapper.insert(logRow);
        } catch (Exception e) {
            log.error("[R1] 订单操作日志写入失败, action={}, orderId={}", action, orderId, e);
        }
    }

    private String requireShipField(String value, String label) {
        String v = StrUtil.trimToNull(value);
        if (v == null) {
            throw new BadRequestException(label + "不能为空", BadRequestException.PARAM_INVALID);
        }
        if (v.length() > 64) {
            throw new BadRequestException(label + "不能超过 64 字", BadRequestException.PARAM_INVALID);
        }
        return v;
    }

    private LocalDateTime parseTime(String value, String field) {
        String v = StrUtil.trimToNull(value);
        if (v == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(v, DT);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(field + " 时间格式应为 yyyy-MM-dd HH:mm:ss", BadRequestException.PARAM_INVALID);
        }
    }

    private List<Integer> parseAftersaleMulti(String statuses) {
        List<Integer> codes = new ArrayList<>();
        for (String raw : statuses.split(",")) {
            String item = StrUtil.trimToEmpty(raw);
            if (item.isEmpty()) {
                return null;
            }
            Integer code = AftersaleStatus.parse(item);
            if (code == null) {
                return null;
            }
            if (!codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    private String goodsSummary(List<OrderGoods> lines) {
        if (lines.isEmpty()) {
            return "";
        }
        String first = StrUtil.nullToEmpty(lines.get(0).getGoodsName());
        if (lines.size() == 1) {
            return first;
        }
        return first + " 等 " + lines.size() + " 件";
    }

    /** 列表/详情用的 goodsList（现状保留字段，P-12） */
    private List<OrderGoodsVO> listOrderGoods(List<OrderGoods> lines) {
        return lines.stream().map(og -> {
            OrderGoodsVO g = new OrderGoodsVO();
            g.setOrderGoodsId(og.getId());
            g.setGoodsId(og.getGoodsId());
            g.setGoodsName(og.getGoodsName());
            g.setPicUrl(og.getPicUrl());
            g.setNumber(og.getNumber());
            g.setPrice(Money.fen(og.getPrice()));
            g.setSpecText(specText(og.getSpecifications()));
            g.setProductId(og.getProductId());
            g.setSpecifications(parseSpecList(og.getSpecifications()));
            g.setRefundStatus(og.getRefundStatus());
            g.setRefundStatusText(AftersaleStatus.refundStatusLabel(og.getRefundStatus()));
            g.setCommentStatus(og.getComment() == null ? 0 : og.getComment());
            return g;
        }).collect(Collectors.toList());
    }

    private OrderListItemVO toListItemVO(Order order, List<OrderGoods> lines) {
        OrderListItemVO vo = new OrderListItemVO();
        vo.setOrderId(order.getId());
        vo.setOrderSn(order.getOrderSn());
        vo.setStatus(OrderStatus.ident(order.getOrderStatus()));
        vo.setStatusText(OrderStatus.label(order.getOrderStatus()));
        vo.setGoodsAmount(Money.fen(order.getGoodsPrice()));
        vo.setFreightAmount(Money.fen(order.getFreightPrice()));
        vo.setCouponAmount(Money.fen(order.getCouponPrice()));
        vo.setOrderAmount(Money.fen(order.getOrderPrice()));
        vo.setFullyRefunded(OrderRules.isFullyRefunded(lines));
        vo.setRefundedAmount(Money.fen(OrderRules.refundedAmount(lines)));
        vo.setHasUnreviewed(OrderRules.hasUnreviewed(lines));
        vo.setExpireTime(Objects.equals(order.getOrderStatus(), OrderStatus.UNPAID)
                ? format(order.getExpireTime()) : null);
        vo.setAddTime(format(order.getAddTime()));
        vo.setGoodsList(listOrderGoods(lines));
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
                .eq(OrderGoods::getDeleted, false)
                .orderByAsc(OrderGoods::getId));
        return all.stream().collect(Collectors.groupingBy(OrderGoods::getOrderId));
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

    /** 规格文案：数组 join(" / ")，无规格返回 null */
    private String specText(String json) {
        List<String> specs = parseSpecList(json);
        if (specs.isEmpty()) {
            return null;
        }
        return String.join(" / ", specs);
    }

    /** 拼接完整收货地址 */
    private String fullAddress(AddressInfoDTO address) {
        return StrUtil.nullToEmpty(address.getProvince())
                + StrUtil.nullToEmpty(address.getCity())
                + StrUtil.nullToEmpty(address.getDistrict())
                + StrUtil.nullToEmpty(address.getAddress());
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.format(DT);
    }

    private List<Long> distinct(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    /**
     * 幂等参数指纹：cartIds（排序去重）+ buyNow + addressId + couponId + message。
     * <p>同 idemToken 但指纹不同 → 409 IDEMPOTENT_CONFLICT（07 §2.2）。</p>
     */
    private String fingerprint(OrderSubmitDTO dto) {
        List<Long> carts = dto.getCartIds() == null ? List.of()
                : dto.getCartIds().stream().filter(Objects::nonNull).sorted().collect(Collectors.toList());
        String buyNow = dto.getBuyNow() == null ? "-"
                : dto.getBuyNow().getProductId() + "x" + dto.getBuyNow().getNumber();
        String raw = carts + "|" + buyNow + "|" + dto.getAddressId() + "|" + dto.getCouponId()
                + "|" + StrUtil.nullToEmpty(dto.getMessage());
        return DigestUtil.md5Hex(raw);
    }

    private void rememberIdemResult(String reqKey, String fingerprint, OrderSubmitVO vo) {
        try {
            String payload = fingerprint + "|" + JSONUtil.toJsonStr(vo);
            stringRedisTemplate.opsForValue().set(reqKey, payload,
                    RedisKeys.ORDER_IDEM_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[M4] 幂等首次结果缓存失败（同键重放将退化为 409）, key={}", reqKey, e);
        }
    }

    private OrderSubmitVO replayIdemResult(String reqKey, String fingerprint) {
        String payload;
        try {
            payload = stringRedisTemplate.opsForValue().get(reqKey);
        } catch (Exception e) {
            log.warn("[M4] 读取幂等首次结果失败, key={}", reqKey, e);
            return null;
        }
        if (payload == null) {
            return null;
        }
        int idx = payload.indexOf('|');
        if (idx <= 0) {
            return null;
        }
        if (!fingerprint.equals(payload.substring(0, idx))) {
            return null;
        }
        try {
            return JSONUtil.toBean(payload.substring(idx + 1), OrderSubmitVO.class);
        } catch (Exception e) {
            log.warn("[M4] 解析幂等首次结果失败, key={}", reqKey, e);
            return null;
        }
    }

    /** USED --(原子)--> ROLLED_BACK（保留原 TTL）；失败不影响主流程 */
    private void rollbackIdemUsed(String idemKey) {
        try {
            stringRedisTemplate.execute(IDEM_ROLLBACK_SCRIPT, List.of(idemKey));
        } catch (Exception e) {
            log.warn("[M4] 幂等 token 回退失败（该 token 将在 TTL 到期后才能重试）, key={}", idemKey, e);
        }
    }

    /**
     * 生成订单编号（M3）：{@code yyyyMMddHHmmss}(14) + 4 位进程内序列(hex) + 4 位随机(hex)，共 22 位。
     */
    private String generateOrderSn() {
        long seq = SN_SEQUENCE.getAndIncrement() & 0xFFFFL;
        int rand = ThreadLocalRandom.current().nextInt(0x10000);
        return LocalDateTime.now().format(SN_FORMAT) + String.format("%04x%04x", seq, rand);
    }

    /**
     * M3：保存订单；若 {@code order_sn} 唯一索引冲突则换号重试（最多 {@link #SN_MAX_RETRY} 次）。
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

    /** 订单行 → 库存变更项（实扣/回补共用；携带 orderGoodsId 供审计） */
    private StockChangeDTO stockChangeOf(List<OrderGoods> lines, Long orderId) {
        List<StockChangeDTO.Item> items = new ArrayList<>(lines.size());
        for (OrderGoods og : lines) {
            if (og.getProductId() == null || og.getNumber() == null) {
                continue;
            }
            StockChangeDTO.Item item = new StockChangeDTO.Item();
            item.setProductId(og.getProductId());
            item.setNumber(og.getNumber());
            item.setOrderGoodsId(og.getId());
            items.add(item);
        }
        StockChangeDTO dto = new StockChangeDTO();
        dto.setItems(items);
        dto.setOrderId(orderId);
        return dto;
    }

    /**
     * 下单行草稿（解析后的商品快照来源）。
     * <p>{@code specText} 由 {@code specifications} JSON 派生，用于 409 STOCK_NOT_ENOUGH 文案。</p>
     */
    private record DraftLine(Long productId, Long goodsId, String goodsName, String goodsSn,
                             BigDecimal price, int number, String specifications, String picUrl,
                             String skuCode, String specValueIds, Integer sellable) {

        String specText() {
            List<String> specs = parseSpecifications(specifications);
            return specs.isEmpty() ? StrUtil.nullToEmpty(goodsName) : String.join(" / ", specs);
        }

        private static List<String> parseSpecifications(String json) {
            if (StrUtil.isBlank(json)) {
                return List.of();
            }
            try {
                return JSONUtil.toList(json, String.class);
            } catch (Exception e) {
                return List.of(json);
            }
        }
    }
}
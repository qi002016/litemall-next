package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import tools.jackson.databind.ObjectMapper;
import com.litemall.domain.PO.Coupon;
import com.litemall.domain.PO.CouponUser;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CouponReceiveResultVO;
import com.litemall.domain.VO.CouponUserVO;
import com.litemall.domain.VO.CouponVO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.UnauthorizedException;
import com.litemall.mapper.CouponMapper;
import com.litemall.mapper.CouponUserMapper;
import com.litemall.mq.CouponReceiveProducer;
import com.litemall.service.ICouponService;
import com.litemall.utils.BeanUtils;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券信息及规则表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 领券防刷限流：同用户同券 60 秒内仅允许一次 */
    private static final Duration RECEIVE_INTERVAL = Duration.ofSeconds(60);
    private static final String RECEIVE_LOCK_KEY = "coupon:receive:";
    private static final String STOCK_KEY = "coupon:stock:";
    private static final String RECEIVED_KEY = "coupon:received:";
    private static final String RESULT_KEY = "coupon:result:";

    private final StringRedisTemplate stringRedisTemplate;
    private final CouponUserMapper couponUserMapper;
    private final CouponReceiveProducer producer;
    private final ObjectMapper objectMapper;

    @Override
    public PageVO<CouponVO> getCouponList(Integer pageNum, Integer pageSize) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Page<Coupon> page = new Page<>(pn, ps);
        LocalDateTime now = LocalDateTime.now();
        // 当前可领取 = 未删除 + 正常状态(0) + 用户领取类型(0)
        // 且时间上：永久券(start/end 任一为 NULL) 或 限时券(now 在有效期内)
        Page<Coupon> couponPage = lambdaQuery()
                .eq(Coupon::getDeleted, false)
                .eq(Coupon::getStatus, 0)
                .eq(Coupon::getType, 0)
                .and(w -> w
                        .and(p -> p.isNull(Coupon::getStartTime).or().isNull(Coupon::getEndTime))
                        .or(p -> p.le(Coupon::getStartTime, now).ge(Coupon::getEndTime, now)))
                .page(page);
        List<Coupon> couponList = couponPage.getRecords();
        List<CouponVO> couponVOList = couponList.stream().map(coupon -> {
            CouponVO vo = BeanUtils.copyBean(coupon, CouponVO.class);
            vo.setLeftCount(coupon.getTotal());
            // PO 是 LocalDateTime，VO 是 String，手动格式化（永久券为 null）
            vo.setStartTime(coupon.getStartTime() == null ? null : coupon.getStartTime().format(DT));
            vo.setEndTime(coupon.getEndTime() == null ? null : coupon.getEndTime().format(DT));
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(couponPage.getTotal(), couponPage.getPages(), couponVOList);
    }

    @Override
    public CouponReceiveResultVO receiveCoupon(Long couponId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }

        // 1.防刷：同用户同券 60s 内仅允许一次
        String lockKey = RECEIVE_LOCK_KEY + couponId + ":" + userId;
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", RECEIVE_INTERVAL.getSeconds(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(ok)) {
            throw new BadRequestException("领取过于频繁，请稍后再试");
        }

        // 2.校验券本身
        Coupon coupon = getById(couponId);
        if (coupon == null || Boolean.TRUE.equals(coupon.getDeleted())) {
            throw new BadRequestException("优惠券不存在");
        }
        if (coupon.getStatus() != null && coupon.getStatus() != 0) {
            throw new BadRequestException("优惠券已下架或已过期");
        }
        if (coupon.getType() != null && coupon.getType() != 0) {
            throw new BadRequestException("该优惠券不可主动领取");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean timeLimited = coupon.getTimeType() != null && coupon.getTimeType() == 1;
        if (timeLimited) {
            if (coupon.getStartTime() != null && now.isBefore(coupon.getStartTime())
                    || coupon.getEndTime() != null && now.isAfter(coupon.getEndTime())) {
                throw new BadRequestException("优惠券未开始或已过期");
            }
        }

        // 3.限领计数（Redis）
        int limit = coupon.getLimit() == null ? 1 : coupon.getLimit();
        if (limit > 0) {
            String receivedKey = RECEIVED_KEY + couponId + ":" + userId;
            Long cnt = stringRedisTemplate.opsForValue().increment(receivedKey);
            if (cnt != null && cnt == 1L) {
                // 首次设置过期时间（与券有效期对齐，兜底 1 天）
                stringRedisTemplate.expire(receivedKey, 1, TimeUnit.DAYS);
            }
            if (cnt != null && cnt > limit) {
                throw new BadRequestException("已达到最大领取数量");
            }
        }

        // 4.Redis 预减库存（total=0 无限量则跳过）
        int total = coupon.getTotal() == null ? 0 : coupon.getTotal();
        if (total > 0) {
            String stockKey = STOCK_KEY + couponId;
            // lazy 初始化：key 不存在时从 DB total 写入
            Boolean hasStock = stringRedisTemplate.hasKey(stockKey);
            if (!Boolean.TRUE.equals(hasStock)) {
                stringRedisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(total));
            }
            Long left = stringRedisTemplate.opsForValue().decrement(stockKey);
            if (left == null || left < 0) {
                // 回滚
                stringRedisTemplate.opsForValue().increment(stockKey);
                throw new BadRequestException("优惠券已领完");
            }
        }

        // 5.写 PROCESSING 结果，发 MQ
        String resultKey = RESULT_KEY + couponId + ":" + userId;
        CouponReceiveResultVO pending = new CouponReceiveResultVO();
        pending.setStatus(CouponReceiveResultVO.PROCESSING);
        try {
            stringRedisTemplate.opsForValue().set(resultKey, objectMapper.writeValueAsString(pending),
                    5, TimeUnit.MINUTES);
            producer.send(couponId, userId);
        } catch (Exception e) {
            // 发消息失败：回补库存
            if (total > 0) {
                stringRedisTemplate.opsForValue().increment(STOCK_KEY + couponId);
            }
            log.error("领券发 MQ 失败: couponId={}, userId={}", couponId, userId, e);
            throw new BadRequestException("领取失败，请稍后再试");
        }
        return pending;
    }

    @Override
    public CouponReceiveResultVO getReceiveResult(Long couponId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        String resultKey = RESULT_KEY + couponId + ":" + userId;
        String json = stringRedisTemplate.opsForValue().get(resultKey);
        if (json == null) {
            CouponReceiveResultVO vo = new CouponReceiveResultVO();
            vo.setStatus(CouponReceiveResultVO.PROCESSING);
            return vo;
        }
        try {
            return objectMapper.readValue(json, CouponReceiveResultVO.class);
        } catch (Exception e) {
            throw new BadRequestException("结果解析失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void persistReceive(Long couponId, Long userId) {
        String resultKey = RESULT_KEY + couponId + ":" + userId;
        try {
            Coupon coupon = getById(couponId);
            if (coupon == null || Boolean.TRUE.equals(coupon.getDeleted())
                    || coupon.getStatus() != null && coupon.getStatus() != 0) {
                writeFail(resultKey, "优惠券已不可领取");
                return;
            }

            LocalDateTime now = LocalDateTime.now();
            boolean timeLimited = coupon.getTimeType() != null && coupon.getTimeType() == 1;
            if (timeLimited && (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime()))) {
                writeFail(resultKey, "优惠券已过期");
                return;
            }

            // DB 原子扣库存兜底
            int total = coupon.getTotal() == null ? 0 : coupon.getTotal();
            if (total > 0) {
                boolean deducted = lambdaUpdate()
                        .setSql("total = total - 1")
                        .eq(Coupon::getId, couponId)
                        .gt(Coupon::getTotal, 0)
                        .update();
                if (!deducted) {
                    writeFail(resultKey, "优惠券已领完");
                    return;
                }
            }

            LocalDateTime startTime = timeLimited ? coupon.getStartTime() : now;
            LocalDateTime endTime = timeLimited ? coupon.getEndTime()
                    : (coupon.getDays() == null ? null : now.plusDays(coupon.getDays()));

            CouponUser record = new CouponUser()
                    .setUserId(userId)
                    .setCouponId(couponId)
                    .setStatus((short) 0)
                    .setStartTime(startTime)
                    .setEndTime(endTime)
                    .setAddTime(now)
                    .setUpdateTime(now)
                    .setDeleted(false);
            couponUserMapper.insert(record);

            CouponUserVO vo = new CouponUserVO();
            vo.setId(record.getId());
            vo.setCouponId(couponId);
            vo.setName(coupon.getName());
            vo.setDiscount(coupon.getDiscount());
            vo.setMin(coupon.getMin());
            vo.setEndTime(endTime == null ? null : endTime.format(DT));

            CouponReceiveResultVO result = new CouponReceiveResultVO();
            result.setStatus(CouponReceiveResultVO.SUCCESS);
            result.setCouponUser(vo);
            stringRedisTemplate.opsForValue().set(resultKey, objectMapper.writeValueAsString(result),
                    1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("领券落库失败: couponId={}, userId={}", couponId, userId, e);
            // 回补 Redis 库存
            int total = couponId == null ? 0 : (getById(couponId) != null && getById(couponId).getTotal() != null
                    ? getById(couponId).getTotal() : 0);
            if (total > 0) {
                stringRedisTemplate.opsForValue().increment(STOCK_KEY + couponId);
            }
            writeFail(resultKey, "系统异常，领取失败");
        }
    }

    private void writeFail(String resultKey, String msg) {
        CouponReceiveResultVO fail = new CouponReceiveResultVO();
        fail.setStatus(CouponReceiveResultVO.FAILED);
        fail.setMessage(msg);
        try {
            stringRedisTemplate.opsForValue().set(resultKey, objectMapper.writeValueAsString(fail),
                    1, TimeUnit.HOURS);
        } catch (Exception ignored) {}
    }

    @Override
    public PageVO<CouponUserVO> getCouponUserList(Integer pageNum, Integer pageSize, String status) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Page<CouponUser> page = new Page<>(pn, ps);
        Page<CouponUser> couponUserPage = couponUserMapper.selectPage(page,
                new LambdaQueryWrapper<CouponUser>()
                        .eq(CouponUser::getUserId, UserContext.getUser())
                        .eq(CouponUser::getDeleted, false)
                        .eq(status != null, CouponUser::getStatus, status));
        List<CouponUser> couponUserList = couponUserPage.getRecords();
        if (couponUserList.isEmpty()) {
            return new PageVO<>(couponUserPage.getTotal(), couponUserPage.getPages(), List.of());
        }

        // 批量查询券信息，补充 name/discount/min
        List<Integer> couponIds = couponUserList.stream()
                .map(c -> c.getCouponId().intValue())
                .collect(Collectors.toList());
        Map<Integer, Coupon> couponMap = listByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c, (a, b) -> a));

        List<CouponUserVO> couponUserVOList = couponUserList.stream().map(couponUser -> {
            CouponUserVO vo = BeanUtils.copyBean(couponUser, CouponUserVO.class);
            Coupon coupon = couponMap.get(couponUser.getCouponId().intValue());
            if (coupon != null) {
                vo.setName(coupon.getName());
                vo.setDiscount(coupon.getDiscount());
                vo.setMin(coupon.getMin());
            }
            vo.setEndTime(couponUser.getEndTime() == null ? null : couponUser.getEndTime().format(DT));
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(couponUserPage.getTotal(), couponUserPage.getPages(), couponUserVOList);
    }

    @Override
    public BigDecimal checkCoupon(Long couponUserId, BigDecimal goodsAmount) {
        if (couponUserId == null) {
            return BigDecimal.ZERO;
        }
        CouponUser couponUser = couponUserMapper.selectById(couponUserId);
        if (couponUser == null || Boolean.TRUE.equals(couponUser.getDeleted())
                || couponUser.getStatus() == null || couponUser.getStatus() != 0) {
            return BigDecimal.ZERO;
        }
        // 有效期判断：end_time 为空视为永久券；否则必须在有效期内
        LocalDateTime now = LocalDateTime.now();
        if (couponUser.getEndTime() != null && now.isAfter(couponUser.getEndTime())) {
            return BigDecimal.ZERO;
        }
        Coupon coupon = getById(couponUser.getCouponId());
        if (coupon == null || Boolean.TRUE.equals(coupon.getDeleted())
                || coupon.getStatus() != null && coupon.getStatus() != 0) {
            return BigDecimal.ZERO;
        }
        // 门槛判断：min 为空视为无门槛
        BigDecimal min = coupon.getMin();
        if (min != null && goodsAmount != null && goodsAmount.compareTo(min) < 0) {
            return BigDecimal.ZERO;
        }
        // 抵扣金额不超过商品总额
        BigDecimal discount = coupon.getDiscount() == null ? BigDecimal.ZERO : coupon.getDiscount();
        if (goodsAmount != null && discount.compareTo(goodsAmount) > 0) {
            return goodsAmount;
        }
        return discount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void useCoupon(Long couponUserId, Long orderId) {
        if (couponUserId == null) {
            return;
        }
        int updated = couponUserMapper.update(null, new LambdaUpdateWrapper<CouponUser>()
                .eq(CouponUser::getId, couponUserId)
                .eq(CouponUser::getStatus, 0)
                .eq(CouponUser::getDeleted, false)
                .set(CouponUser::getStatus, (short) 1)
                .set(CouponUser::getUsedTime, LocalDateTime.now())
                .set(CouponUser::getOrderId, orderId)
                .set(CouponUser::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BadRequestException("优惠券不可用");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackCoupon(Long couponUserId, Long orderId) {
        // 优先按用户券记录ID回退；否则按订单ID反查已核销的券
        if (couponUserId == null && orderId != null) {
            List<CouponUser> usedList = couponUserMapper.selectList(new LambdaQueryWrapper<CouponUser>()
                    .eq(CouponUser::getOrderId, orderId)
                    .eq(CouponUser::getStatus, 1)
                    .eq(CouponUser::getDeleted, false));
            for (CouponUser used : usedList) {
                doRollback(used.getId());
            }
            return;
        }
        if (couponUserId != null) {
            doRollback(couponUserId);
        }
    }

    private void doRollback(Long couponUserId) {
        couponUserMapper.update(null, new LambdaUpdateWrapper<CouponUser>()
                .eq(CouponUser::getId, couponUserId)
                .eq(CouponUser::getStatus, 1)
                .eq(CouponUser::getDeleted, false)
                .set(CouponUser::getStatus, (short) 0)
                .set(CouponUser::getUsedTime, null)
                .set(CouponUser::getOrderId, null)
                .set(CouponUser::getUpdateTime, LocalDateTime.now()));
    }
}

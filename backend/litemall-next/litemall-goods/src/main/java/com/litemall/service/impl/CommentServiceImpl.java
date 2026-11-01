package com.litemall.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.OrderClient;
import com.litemall.client.UserClient;
import com.litemall.domain.DTO.CommentAdminQuery;
import com.litemall.domain.DTO.CommentDTO;
import com.litemall.domain.DTO.OrderSnDTO;
import com.litemall.domain.PO.Comment;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CommentPostResultVO;
import com.litemall.domain.VO.CommentVO;
import com.litemall.domain.dto.CommentAdminVO;
import com.litemall.domain.dto.CommentReplyDTO;
import com.litemall.domain.dto.CommentReplyResultDTO;
import com.litemall.domain.dto.CommentStatusDTO;
import com.litemall.domain.dto.CommentStatusResultDTO;
import com.litemall.domain.dto.OrderGoodsStatusDTO;
import com.litemall.domain.dto.OrderTryCompleteDTO;
import com.litemall.domain.dto.OrderTryCompleteResultDTO;
import com.litemall.domain.dto.UserInfoDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.GoodsApiException;
import com.litemall.mapper.CommentMapper;
import com.litemall.mapper.GoodsMapper;
import com.litemall.mapper.OrderSnRefMapper;
import com.litemall.service.ICommentService;
import com.litemall.support.GoodsSupport;
import com.litemall.support.GoodsLogErrorCounter;
import com.litemall.utils.UserContext;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 评论表 服务实现类
 * </p>
 *
 * <p>
 * R2（05 §2.3 / §3.9~§3.11）在本类收口评价域的全部读写：
 * </p>
 * <ul>
 *   <li><b>CI-35</b> {@link #getCommentList}：C 端只读 {@code status=0}，total 也只计未隐藏（BR-CM-04）；</li>
 *   <li><b>CI-36</b> {@link #addComment}：按九步校验链锚订单行落库，事务<b>提交后</b>再调
 *       order 域 CN-IN-08（05 §2.3.4，禁止事务内调用）；</li>
 *   <li><b>CN-29~31</b> {@link #adminListComments}/{@link #replyComment}/{@link #updateCommentStatus}：
 *       管理端含隐藏项、覆盖写回复、CAS 隐藏/恢复。</li>
 * </ul>
 * <p>
 * 红线（05 §1.7）：{@code litemall_comment} 唯一写服务是 goods；本类<b>不</b>直写
 * {@code order_goods.comment} 与 {@code order.status}，301→501 只能由 order 域执行；
 * {@code litemall_log} 也不写（唯一写服务是 admin），同步失败只 WARN + 计数。
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements ICommentService {

    /** 评价内容上限（05 §2.3.1：有值才校验 ≤500） */
    private static final int MAX_CONTENT_LENGTH = 500;

    /** 评价图片上限 */
    private static final int MAX_PICTURE_COUNT = 6;

    /** 单张图片地址上限 */
    private static final int MAX_PICTURE_URL_LENGTH = 255;

    /** 商家回复上限（05 §3.10：1–500） */
    private static final int MAX_REPLY_LENGTH = 500;

    /** 301 = 已收货（BR-CM-01：只有已收货可评） */
    private static final int ORDER_STATUS_RECEIVED = 301;

    /** 2 = 该行已全额退款（05 §2.3.3 步 7） */
    private static final int REFUND_STATUS_FULL = 2;

    /** 订单完成态同步的总尝试次数（首次 + 立即重试 1 次，05 §2.3.4） */
    private static final int ORDER_COMPLETE_ATTEMPTS = 2;

    /** 管理端状态文案（05 §3.9 冻结） */
    private static final String STATUS_TEXT_VISIBLE = "显示";

    private static final String STATUS_TEXT_HIDDEN = "已隐藏";

    private final UserClient userClient;

    private final OrderClient orderClient;

    private final GoodsMapper goodsMapper;

    private final OrderSnRefMapper orderSnRefMapper;

    private final GoodsLogErrorCounter goodsLogErrorCounter;

    /* ==================== CI-35：C 端评价列表 ==================== */

    @Override
    public PageVO<CommentVO> getCommentList(Long goodsId, Integer star, Boolean hasPicture,
                                            int pageNum, int pageSize) {
        if (goodsId == null) {
            throw new BadRequestException("商品ID不能为空");
        }
        Page<Comment> page = new Page<>(pageNum, pageSize);
        // 1.分页查询商品评论(type=0 商品评论, value_id=商品ID)
        //    只返回 status=0（显示）的评价；status=1（隐藏）由后台管理隐藏，C 端不可见（BR-CM-04）
        //    R2 新增 star / hasPicture 两个筛选维度（08 §3.2 建议新增，均为纯增量）；total 与列表同口径
        Page<Comment> p = lambdaQuery()
                .eq(Comment::getValueId, goodsId)
                .eq(Comment::getType, 0)
                .eq(Comment::getStatus, 0)
                .eq(Comment::getDeleted, false)
                .eq(star != null, Comment::getStar, star)
                .eq(hasPicture != null, Comment::getHasPicture, hasPicture)
                .orderByDesc(Comment::getAddTime)
                .orderByDesc(Comment::getId)
                .page(page);
        List<Comment> comments = p.getRecords();
        if (comments == null || comments.isEmpty()) {
            return PageVO.empty(p);
        }
        // 2.通过Feign客户端跨服务批量查询评论用户信息(昵称/头像)
        Map<Long, UserInfoDTO> userMap = loadUsers(comments);
        // 3.组装VO
        List<CommentVO> voList = comments.stream().map(c -> {
            // 手工装配：addTime/replyTime 需要转成 yyyy-MM-dd HH:mm:ss 字符串，
            // 用 BeanUtils.copyBean 会依赖 Hutool 的类型推断，时间字段行为不确定
            CommentVO vo = new CommentVO();
            vo.setId(c.getId());
            vo.setContent(c.getContent());
            vo.setStar(c.getStar());
            vo.setPicUrls(parsePicUrls(c.getPicUrls()));
            vo.setHasPicture(Boolean.TRUE.equals(c.getHasPicture()) || vo.getPicUrls().length > 0);
            // 时间统一 yyyy-MM-dd HH:mm:ss（06 §1.3 出参口径）
            vo.setAddTime(GoodsSupport.formatTime(c.getAddTime()));
            vo.setReplyTime(GoodsSupport.formatTime(c.getReplyTime()));
            vo.setAdminContent(StringUtils.hasText(c.getAdminContent()) ? c.getAdminContent() : null);
            UserInfoDTO user = userMap.get(c.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
            }
            return vo;
        }).collect(Collectors.toList());
        return PageVO.of(p, voList);
    }

    /* ==================== CI-36：发表评价 ==================== */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentPostResultVO addComment(CommentDTO dto) {
        // 步 0：登录态（网关已保证 role=user；本地双保险，userId 只认上下文，不认入参）
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }
        // 步 1：参数（控制器 jakarta 校验之外再兜一次，防内部调用绕过）
        validatePostParams(dto);
        // 步 2：CN-IN-01 订单行校验（不传 userId：是否本人由本地比对，见步 3）
        OrderGoodsStatusDTO orderGoods = loadOrderGoods(dto.getOrderGoodsId());
        // 步 3：订单归属（403，防越权评价他人订单）
        if (!Objects.equals(orderGoods.getUserId(), userId)) {
            throw GoodsApiException.notOrderOwner();
        }
        // 步 4：订单行必须属于入参订单
        if (!Objects.equals(orderGoods.getOrderId(), dto.getOrderId())) {
            throw GoodsApiException.paramInvalid("订单行不属于该订单");
        }
        // 步 5：只有已收货（301）可评
        if (!Objects.equals(orderGoods.getOrderStatus(), ORDER_STATUS_RECEIVED)) {
            throw GoodsApiException.orderGoodsNotReceived();
        }
        // 步 6：未评价（commented/commentId 由 order 域给出；唯一键是最终保障，见步 8）
        if (Boolean.TRUE.equals(orderGoods.getCommented()) || orderGoods.getCommentId() != null) {
            throw GoodsApiException.commentAlreadyExists();
        }
        // 步 7：该行未全额退款
        if (Objects.equals(orderGoods.getRefundStatus(), REFUND_STATUS_FULL)) {
            throw GoodsApiException.orderGoodsRefunded();
        }
        // 步 8：落库（valueId 取订单行的 goodsId，不采信入参 goodsId）
        Comment comment = new Comment();
        comment.setValueId(orderGoods.getGoodsId());
        comment.setType(0);
        comment.setOrderId(orderGoods.getOrderId());
        comment.setOrderGoodsId(orderGoods.getOrderGoodsId());
        comment.setUserId(userId);
        comment.setStar(dto.getStar());
        String content = dto.getContent() == null ? null : dto.getContent().trim();
        comment.setContent(content == null || content.isEmpty() ? null : content);
        List<String> picUrls = normalizePicUrls(dto.getPicUrls());
        comment.setHasPicture(!picUrls.isEmpty());
        comment.setPicUrls(picUrls.isEmpty() ? null : JSONUtil.toJsonStr(picUrls));
        comment.setStatus(0);
        comment.setReplyTime(null);
        comment.setDeleted(false);
        LocalDateTime now = LocalDateTime.now();
        comment.setAddTime(now);
        comment.setUpdateTime(now);
        try {
            save(comment);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // uk_comment_order_goods 兜底并发重复评价（05 §5.8）：必须 409，绝不 500
            throw GoodsApiException.commentAlreadyExists();
        }
        Long commentId = comment.getId();
        Long orderId = orderGoods.getOrderId();
        Long orderGoodsId = orderGoods.getOrderGoodsId();
        // 步 9：事务提交后再同步订单完成态（禁止事务内调用，05 §2.3.4）
        // 出参对象先建好，afterCommit 里按 order 域结果回填 orderStatusUpdated：
        // afterCommit 与本方法同线程、且在代理返回给控制器之前执行，因此回填对调用方可见
        CommentPostResultVO result = CommentPostResultVO.of(commentId, false);
        runAfterCommit(() -> syncOrderComplete(orderId, orderGoodsId, commentId, result));
        return result;
    }

    /**
     * 事务提交后执行（无事务上下文时立即执行）。
     *
     * @param action 提交后动作
     */
    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /**
     * 同步订单完成态（301→501）：调 order 域 CN-IN-08，最多 2 次尝试。
     * <p>
     * 全过程兜底且<b>绝不抛出</b>：评价已经提交，不能因为订单状态同步失败让用户看到失败；
     * 两次都失败时写 WARN + {@link GoodsLogErrorCounter} 计数（05 §2.3.4，残留风险 D-40 / 裁决 2.9）。
     * </p>
     *
     * @param orderId     订单ID
     * @param orderGoodsId 订单行ID
     * @param commentId   评价ID
     * @param result      出参（成功推进为 501 时回填 {@code orderStatusUpdated=true}）
     */
    private void syncOrderComplete(Long orderId, Long orderGoodsId, Long commentId,
                                   CommentPostResultVO result) {
        for (int attempt = 1; attempt <= ORDER_COMPLETE_ATTEMPTS; attempt++) {
            try {
                OrderTryCompleteDTO param = new OrderTryCompleteDTO();
                param.setOrderGoodsId(orderGoodsId);
                param.setCommentId(commentId);
                OrderTryCompleteResultDTO response = orderClient.tryCompleteOrder(orderId, param);
                // changed=false 是幂等命中（订单已非 301），不算故障
                if (response != null && Boolean.TRUE.equals(response.getChanged())) {
                    result.setOrderStatusUpdated(Boolean.TRUE);
                    log.info("评价后订单已同步完成：orderId={}, orderGoodsId={}, commentId={}",
                            orderId, orderGoodsId, commentId);
                }
                return;
            } catch (Exception e) {
                log.warn("评价后同步订单完成态失败（第 {}/{} 次）：orderId={}, orderGoodsId={}, cause={}",
                        attempt, ORDER_COMPLETE_ATTEMPTS, orderId, orderGoodsId, e.toString());
            }
        }
        // 05 §2.3.4 + 裁决 2.9：两次失败 ⇒ WARN + 日志异常计数；不写 litemall_log（该表唯一写服务是 admin，§1.7）
        goodsLogErrorCounter.increase();
        log.warn("评价后同步订单完成态最终失败，订单可能停留在 301：orderId={}, orderGoodsId={}, commentId={},"
                        + " 累计失败次数={}",
                orderId, orderGoodsId, commentId, goodsLogErrorCounter.localCount());
    }

    /* ==================== CN-29：管理端评价列表 ==================== */

    @Override
    public PageVO<CommentAdminVO> adminListComments(CommentAdminQuery query) {
        query.normalize();
        Page<Comment> page = new Page<>(query.getPageNum(), query.getPageSize());
        // 与 C 端口径相反：管理端 total 与列表都含已隐藏（05 §5.7，禁止复用同一 SQL）
        com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper<Comment> wrapper = lambdaQuery()
                .eq(Comment::getType, 0)
                .eq(Comment::getDeleted, false)
                .eq(query.getGoodsId() != null, Comment::getValueId, query.getGoodsId())
                .like(StringUtils.hasText(query.getKeyword()), Comment::getContent, query.getKeyword())
                .in(!query.getStars().isEmpty(), Comment::getStar, query.getStars())
                .in(!query.getStatuses().isEmpty(), Comment::getStatus, query.getStatuses())
                .ge(query.getFromTime() != null, Comment::getAddTime, query.getFromTime())
                .le(query.getToTime() != null, Comment::getAddTime, query.getToTime());
        // replied 过滤：已回复 = admin_content 非空串；未回复 = 空值或空串（BR-CM-03）
        if (query.getReplied() != null) {
            if (Boolean.TRUE.equals(query.getReplied())) {
                wrapper.isNotNull(Comment::getAdminContent).ne(Comment::getAdminContent, "");
            } else {
                wrapper.and(w -> w.isNull(Comment::getAdminContent).or().eq(Comment::getAdminContent, ""));
            }
        }
        Page<Comment> p = wrapper.orderByDesc(Comment::getAddTime)
                .orderByDesc(Comment::getId)
                .page(page);
        List<Comment> comments = p.getRecords();
        if (comments == null || comments.isEmpty()) {
            return PageVO.empty(p);
        }
        Map<Long, UserInfoDTO> userMap = loadUsers(comments);
        Map<Long, String> goodsNames = loadGoodsNames(comments);
        Map<Long, String> orderSns = loadOrderSns(comments);
        List<CommentAdminVO> rows = new ArrayList<>(comments.size());
        for (Comment c : comments) {
            CommentAdminVO vo = new CommentAdminVO();
            vo.setId(c.getId());
            vo.setGoodsId(c.getValueId());
            vo.setGoodsName(goodsNames.get(c.getValueId()));
            vo.setOrderId(c.getOrderId());
            vo.setOrderSn(c.getOrderId() == null ? null : orderSns.get(c.getOrderId()));
            vo.setOrderGoodsId(c.getOrderGoodsId());
            vo.setUserId(c.getUserId());
            UserInfoDTO user = userMap.get(c.getUserId());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
            }
            vo.setStar(c.getStar());
            vo.setContent(c.getContent());
            vo.setPicUrls(new ArrayList<>(List.of(parsePicUrls(c.getPicUrls()))));
            vo.setHasPicture(Boolean.TRUE.equals(c.getHasPicture()) || !vo.getPicUrls().isEmpty());
            vo.setStatus(c.getStatus());
            vo.setStatusText(statusText(c.getStatus()));
            boolean replied = StringUtils.hasText(c.getAdminContent());
            vo.setReplied(replied);
            vo.setAdminContent(replied ? c.getAdminContent() : null);
            vo.setReplyTime(GoodsSupport.formatTime(c.getReplyTime()));
            vo.setAddTime(GoodsSupport.formatTime(c.getAddTime()));
            rows.add(vo);
        }
        return PageVO.of(p, rows);
    }

    /* ==================== CN-30：回复评价 ==================== */

    @Override
    public CommentReplyResultDTO replyComment(CommentReplyDTO dto) {
        if (dto == null || dto.getCommentId() == null) {
            throw GoodsApiException.paramInvalid("评价ID不能为空");
        }
        String content = dto.getContent() == null ? null : dto.getContent().trim();
        if (content == null || content.isEmpty()) {
            throw GoodsApiException.paramInvalid("回复内容不能为空");
        }
        if (content.length() > MAX_REPLY_LENGTH) {
            throw GoodsApiException.paramInvalid("回复内容最多 " + MAX_REPLY_LENGTH + " 字");
        }
        LocalDateTime now = LocalDateTime.now();
        // 覆盖写：reply_time 每次刷新（05 §3.10）
        boolean updated = lambdaUpdate()
                .eq(Comment::getId, dto.getCommentId())
                .eq(Comment::getDeleted, false)
                .set(Comment::getAdminContent, content)
                .set(Comment::getReplyTime, now)
                .set(Comment::getUpdateTime, now)
                .update();
        if (!updated) {
            throw GoodsApiException.commentNotFound();
        }
        return CommentReplyResultDTO.of(dto.getCommentId(), content, GoodsSupport.formatTime(now));
    }

    /* ==================== CN-31：隐藏 / 恢复评价 ==================== */

    @Override
    public CommentStatusResultDTO updateCommentStatus(CommentStatusDTO dto) {
        if (dto == null || dto.getCommentId() == null) {
            throw GoodsApiException.paramInvalid("评价ID不能为空");
        }
        Integer target = dto.getStatus();
        if (target == null || (target != 0 && target != 1)) {
            throw GoodsApiException.paramInvalid("status 只能为 0 或 1");
        }
        // CAS：WHERE id=? AND status<>目标 AND deleted=0（05 §3.11），避免并发互相覆盖
        boolean updated = lambdaUpdate()
                .eq(Comment::getId, dto.getCommentId())
                .eq(Comment::getDeleted, false)
                .ne(Comment::getStatus, target)
                .set(Comment::getStatus, target)
                .set(Comment::getUpdateTime, LocalDateTime.now())
                .update();
        if (updated) {
            return CommentStatusResultDTO.of(dto.getCommentId(), target, statusText(target));
        }
        Comment current = getById(dto.getCommentId());
        if (current == null || Boolean.TRUE.equals(current.getDeleted())) {
            throw GoodsApiException.commentNotFound();
        }
        if (Objects.equals(current.getStatus(), target)) {
            // E-3：已是目标态 ⇒ 幂等 200，不报冲突
            return CommentStatusResultDTO.of(dto.getCommentId(), target, statusText(target));
        }
        throw GoodsApiException.commentStatusConflict();
    }

    /* ==================== 内部工具 ==================== */

    /**
     * 评价入参的服务端校验（05 §2.3.3 步 1）。控制器已用 jakarta 注解拦过一次，这里防内部调用绕过。
     */
    private void validatePostParams(CommentDTO dto) {
        if (dto == null) {
            throw GoodsApiException.paramInvalid("请求体不能为空");
        }
        if (dto.getOrderId() == null) {
            throw GoodsApiException.paramInvalid("订单ID不能为空");
        }
        if (dto.getOrderGoodsId() == null) {
            throw GoodsApiException.paramInvalid("订单行ID不能为空");
        }
        Integer star = dto.getStar();
        if (star == null || star < 1 || star > 5) {
            throw GoodsApiException.paramInvalid("评分范围为1-5");
        }
        if (dto.getContent() != null && dto.getContent().trim().length() > MAX_CONTENT_LENGTH) {
            throw GoodsApiException.paramInvalid("评论内容最多 " + MAX_CONTENT_LENGTH + " 字");
        }
        List<String> picUrls = dto.getPicUrls();
        if (picUrls != null) {
            if (picUrls.size() > MAX_PICTURE_COUNT) {
                throw GoodsApiException.paramInvalid("最多上传 " + MAX_PICTURE_COUNT + " 张图片");
            }
            for (String url : picUrls) {
                if (url != null && url.length() > MAX_PICTURE_URL_LENGTH) {
                    throw GoodsApiException.paramInvalid("图片地址最多 " + MAX_PICTURE_URL_LENGTH + " 字符");
                }
            }
        }
    }

    /**
     * 调 CN-IN-01 取订单行（05 §2.3.3 步 2）：订单行不存在 ⇒ 404「订单行不存在」；
     * 其它失败（order 服务不可达 / 5xx）⇒ 500，不得伪装成业务拒绝。
     *
     * @param orderGoodsId 订单行ID
     * @return 订单行评价校验信息
     */
    private OrderGoodsStatusDTO loadOrderGoods(Long orderGoodsId) {
        OrderGoodsStatusDTO orderGoods;
        try {
            orderGoods = orderClient.orderGoodsStatus(orderGoodsId, null);
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw GoodsApiException.notFound("订单行不存在");
            }
            throw new IllegalStateException("订单服务调用失败：orderGoodsId=" + orderGoodsId, e);
        }
        if (orderGoods == null) {
            throw GoodsApiException.notFound("订单行不存在");
        }
        return orderGoods;
    }

    /**
     * 归一评价图片：去 null / 去空白 / 去重（保持顺序）。
     *
     * @param picUrls 原始图片列表
     * @return 归一后的列表（永不为 null）
     */
    private static List<String> normalizePicUrls(List<String> picUrls) {
        if (picUrls == null || picUrls.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String url : picUrls) {
            if (StringUtils.hasText(url)) {
                unique.add(url.trim());
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * {@code pic_urls} JSON 串 → 数组；解析失败按空数组（05 §3.9 口径，绝不 500）。
     *
     * @param raw JSON 数组字符串
     * @return 图片数组（永不为 null）
     */
    private static String[] parsePicUrls(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new String[0];
        }
        try {
            List<String> list = JSONUtil.toList(raw, String.class);
            return list == null ? new String[0] : list.stream().filter(Objects::nonNull).toArray(String[]::new);
        } catch (Exception e) {
            log.warn("评价图片解析失败，按空数组处理：raw={}", raw);
            return new String[0];
        }
    }

    /**
     * 状态文案：0 显示 / 1 已隐藏（05 §3.9 冻结字面量）。
     *
     * @param status 状态码
     * @return 文案
     */
    private static String statusText(Integer status) {
        return Objects.equals(status, 1) ? STATUS_TEXT_HIDDEN : STATUS_TEXT_VISIBLE;
    }

    /**
     * 批量取评价人信息（不可达时为 null，调用方按「不编造」处理）。
     *
     * @param comments 评价行
     * @return userId → 用户信息
     */
    private Map<Long, UserInfoDTO> loadUsers(List<Comment> comments) {
        Set<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserInfoDTO> userInfos;
        try {
            userInfos = userClient.listUserByIds(List.copyOf(userIds));
        } catch (Exception e) {
            log.warn("评价人信息查询失败，nickname/avatar 置空：{}", e.toString());
            return Map.of();
        }
        return userInfos == null ? Map.of()
                : userInfos.stream()
                .filter(u -> u != null && u.getId() != null)
                .collect(Collectors.toMap(UserInfoDTO::getId, Function.identity(), (a, b) -> a));
    }

    /**
     * 批量取商品名（管理端评价行展示用）。
     *
     * @param comments 评价行
     * @return goodsId → 商品名
     */
    private Map<Long, String> loadGoodsNames(List<Comment> comments) {
        Set<Long> goodsIds = comments.stream()
                .map(Comment::getValueId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (goodsIds.isEmpty()) {
            return Map.of();
        }
        List<Goods> goods;
        try {
            goods = goodsMapper.selectBatchIds(goodsIds);
        } catch (Exception e) {
            log.warn("评价商品名查询失败，goodsName 置空：{}", e.toString());
            return Map.of();
        }
        if (goods == null || goods.isEmpty()) {
            return Map.of();
        }
        return goods.stream()
                .filter(g -> g != null && g.getId() != null)
                .collect(Collectors.toMap(Goods::getId, g -> g.getName() == null ? "" : g.getName(), (a, b) -> a));
    }

    /**
     * 批量取订单号（跨域只读，只用于展示）。
     *
     * @param comments 评价行
     * @return orderId → orderSn
     */
    private Map<Long, String> loadOrderSns(List<Comment> comments) {
        Set<Long> orderIds = comments.stream()
                .map(Comment::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<OrderSnDTO> rows = orderSnRefMapper.selectOrderSns(orderIds);
            if (rows == null || rows.isEmpty()) {
                return Map.of();
            }
            return rows.stream()
                    .filter(r -> r != null && r.getId() != null)
                    .collect(Collectors.toMap(OrderSnDTO::getId, r -> r.getOrderSn() == null ? "" : r.getOrderSn(),
                            (a, b) -> a));
        } catch (Exception e) {
            log.warn("评价订单号查询失败，orderSn 置空：{}", e.toString());
            return Map.of();
        }
    }
}
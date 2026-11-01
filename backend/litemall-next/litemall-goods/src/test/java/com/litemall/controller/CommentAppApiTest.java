package com.litemall.controller;

import com.litemall.client.OrderClient;
import com.litemall.client.UserClient;
import com.litemall.domain.PO.Comment;
import com.litemall.domain.dto.OrderGoodsStatusDTO;
import com.litemall.domain.dto.OrderTryCompleteDTO;
import com.litemall.domain.dto.OrderTryCompleteResultDTO;
import com.litemall.mapper.CommentMapper;
import com.litemall.support.GoodsLogErrorCounter;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R2-04 评价域 C 端接口接线级测试（05 §2.3、§3.2）。
 *
 * <p>
 * 覆盖两件事，都用真实 MySQL + MockMvc（Feign 对端用 {@link MockitoBean} 打桩）：
 * </p>
 * <ul>
 *   <li><b>CI-35</b> {@code GET /app/goods/comment/list}：只返回 {@code status=0}、total 同口径，
 *       {@code star}/{@code hasPicture} 筛选与分页；</li>
 *   <li><b>CI-36</b> {@code POST /app/goods/comment/post}：九步校验链的<b>每一步拒绝</b>（404/403/400/409）、
 *       落库字段（{@code value_id} 取订单行 goodsId，不采信入参）、唯一键撞键必须 409 不得 500、
 *       以及事务提交后调 CN-IN-08 成功/失败两条路径（失败仍 200，05 §2.3.4）。</li>
 * </ul>
 * <p>
 * 夹具用 {@code 9_000_000 + 时间戳后缀} 造独立的 goodsId/orderId，避开种子数据；评价行
 * {@code order_goods_id} 留空（MySQL 唯一键对 NULL 不去重），finally 物理删除。
 * </p>
 */
@SpringBootTest
class CommentAppApiTest {

    private static final long GOODS_ID = 9_000_000L + System.currentTimeMillis() % 1_000_000;

    private static final long USER_ID = 700_000L + System.currentTimeMillis() % 1_000;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private GoodsLogErrorCounter goodsLogErrorCounter;

    @MockitoBean
    private OrderClient orderClient;

    @MockitoBean
    private UserClient userClient;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    /* ==================== CI-35：C 端评价列表 ==================== */

    @Test
    @DisplayName("CI-35：只返回 status=0，total 也只计未隐藏（BR-CM-04）")
    void listOnlyVisible() throws Exception {
        long goodsId = GOODS_ID + 1;
        Long visible5 = insertComment(goodsId, 5, true, 0);
        Long visible3 = insertComment(goodsId, 3, false, 0);
        Long hidden = insertComment(goodsId, 5, false, 1);
        try {
            mockMvc().perform(get("/app/goods/comment/list")
                            .param("goodsId", String.valueOf(goodsId))
                            .param("pageNum", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.list.length()").value(2))
                    .andExpect(jsonPath("$.data.list[0].id").exists());
            // 隐藏项确实还在库里，只是 C 端不可见
            assertThat(commentMapper.selectById(hidden)).isNotNull();
            assertThat(visible5).isNotEqualTo(visible3);
        } finally {
            cleanup(goodsId);
        }
    }

    @Test
    @DisplayName("CI-35：star 与 hasPicture 为纯增量筛选，非法 star 返回 400 且 HTTP 仍 200")
    void listFilters() throws Exception {
        long goodsId = GOODS_ID + 2;
        insertComment(goodsId, 5, true, 0);
        insertComment(goodsId, 3, false, 0);
        try {
            mockMvc().perform(get("/app/goods/comment/list")
                            .param("goodsId", String.valueOf(goodsId))
                            .param("star", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.list[0].star").value(5));

            mockMvc().perform(get("/app/goods/comment/list")
                            .param("goodsId", String.valueOf(goodsId))
                            .param("hasPicture", "false"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.list[0].star").value(3));

            // 08 §3.2：star 取值 1–5，越界即参数非法；对外 HTTP 恒 200（01 §1.4）
            mockMvc().perform(get("/app/goods/comment/list")
                            .param("goodsId", String.valueOf(goodsId))
                            .param("star", "6"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.data.errorCode").value("PARAM_INVALID"));
        } finally {
            cleanup(goodsId);
        }
    }

    @Test
    @DisplayName("CI-35：分页口径正确、逐页取完不重不漏")
    void listPagination() throws Exception {
        long goodsId = GOODS_ID + 3;
        for (int i = 0; i < 3; i++) {
            insertComment(goodsId, 5, false, 0);
        }
        try {
            mockMvc().perform(get("/app/goods/comment/list")
                            .param("goodsId", String.valueOf(goodsId))
                            .param("pageNum", "1")
                            .param("pageSize", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(3))
                    .andExpect(jsonPath("$.data.pages").value(2))
                    .andExpect(jsonPath("$.data.list.length()").value(2));

            mockMvc().perform(get("/app/goods/comment/list")
                            .param("goodsId", String.valueOf(goodsId))
                            .param("pageNum", "2")
                            .param("pageSize", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.list.length()").value(1));
        } finally {
            cleanup(goodsId);
        }
    }

    /* ==================== CI-36：发表评价 ==================== */

    @Test
    @DisplayName("CI-36：全链通过 ⇒ 落库并用订单行的 goodsId，提交后同步订单为已完成")
    void postSuccess() throws Exception {
        long goodsId = GOODS_ID + 10;
        long orderId = GOODS_ID + 20;
        long orderGoodsId = GOODS_ID + 30;
        stubOrderGoods(orderId, orderGoodsId, goodsId, USER_ID, 301, 1, false);
        OrderTryCompleteResultDTO done = new OrderTryCompleteResultDTO();
        done.setOrderId(orderId);
        done.setOrderStatus(501);
        done.setChanged(true);
        when(orderClient.tryCompleteOrder(eq(orderId), any())).thenReturn(done);
        try {
            String body = "{\"orderId\":" + orderId + ",\"orderGoodsId\":" + orderGoodsId
                    + ",\"star\":5,\"content\":\"  很好用  \",\"picUrls\":[\"http://img/1.jpg\"]}";
            mockMvc().perform(post("/app/goods/comment/post")
                            .header("user-info", String.valueOf(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.commentId").exists())
                    // changed=true ⇒ orderStatusUpdated=true（05 §2.3.4 出参语义）
                    .andExpect(jsonPath("$.data.orderStatusUpdated").value(true));

            // CN-IN-08 用订单行 + 新评价ID 调用，且只调一次
            ArgumentCaptor<OrderTryCompleteDTO> captor = ArgumentCaptor.forClass(OrderTryCompleteDTO.class);
            verify(orderClient, times(1)).tryCompleteOrder(eq(orderId), captor.capture());
            assertThat(captor.getValue().getOrderGoodsId()).isEqualTo(orderGoodsId);
            assertThat(captor.getValue().getCommentId()).isNotNull();

            // 落库字段：value_id 取订单行的 goodsId（不采信入参）、type=0、status=0、add_time 有值
            Comment saved = findByOrderGoodsId(orderGoodsId);
            assertThat(saved).isNotNull();
            assertThat(saved.getValueId()).isEqualTo(goodsId);
            assertThat(saved.getType()).isZero();
            assertThat(saved.getStatus()).isZero();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getStar()).isEqualTo(5);
            assertThat(saved.getContent()).isEqualTo("很好用");
            assertThat(saved.getHasPicture()).isTrue();
            assertThat(saved.getAddTime()).isNotNull();
            assertThat(captor.getValue().getCommentId()).isEqualTo(saved.getId());
        } finally {
            cleanup(goodsId);
        }
    }

    @Test
    @DisplayName("CI-36：九步链步 1（参数）先于业务校验 ⇒ 400 PARAM_INVALID，且不调 CN-IN-01/CN-IN-08")
    void postParamInvalid() throws Exception {
        String tooLongContent = "x".repeat(501);
        String sevenPics = "{\"orderId\":1,\"orderGoodsId\":2,\"star\":5,\"picUrls\":["
                + "\"http://img/1.jpg\",\"http://img/2.jpg\",\"http://img/3.jpg\","
                + "\"http://img/4.jpg\",\"http://img/5.jpg\",\"http://img/6.jpg\",\"http://img/7.jpg\"]}";
        String oversizedPic = "{\"orderId\":1,\"orderGoodsId\":2,\"star\":5,\"picUrls\":[\""
                + "u".repeat(256) + "\"]}";
        String missingOrderGoodsId = "{\"orderId\":1,\"star\":5}";

        // star 越界（08 §3.1 star 1–5）
        assertParamInvalid("{\"orderId\":1,\"orderGoodsId\":2,\"star\":0}", "评分范围为1-5");
        // content 有值才校验 ≤500（05 §2.3.1）
        assertParamInvalid("{\"orderId\":1,\"orderGoodsId\":2,\"star\":5,\"content\":\""
                + tooLongContent + "\"}", "评论内容最多 500 字");
        // picUrls 最多 6 张、每张 ≤255
        assertParamInvalid(sevenPics, "最多上传 6 张图片");
        assertParamInvalid(oversizedPic, "图片地址最多 255 字符");
        // orderGoodsId 必填（锚订单行）
        assertParamInvalid(missingOrderGoodsId, "订单行ID不能为空");

        // 步 1 失败就不该碰 order 域
        verify(orderClient, never()).orderGoodsStatus(any(), any());
        verify(orderClient, never()).tryCompleteOrder(any(), any());
    }

    /** 断言「HTTP 仍是 200、code=400、errorCode=PARAM_INVALID、msg 含指定文案」 */
    private void assertParamInvalid(String body, String msgFragment) throws Exception {
        mockMvc().perform(post("/app/goods/comment/post")
                        .header("user-info", String.valueOf(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("PARAM_INVALID"))
                .andExpect(jsonPath("$.msg").value(org.hamcrest.Matchers.containsString(msgFragment)));
    }

    @Test
    @DisplayName("CI-36：已评价 ⇒ 409 COMMENT_ALREADY_EXISTS，不落库、不调 CN-IN-08")
    void postDuplicate() throws Exception {
        long goodsId = GOODS_ID + 11;
        long orderId = GOODS_ID + 21;
        long orderGoodsId = GOODS_ID + 31;
        stubOrderGoods(orderId, orderGoodsId, goodsId, USER_ID, 301, 1, true);
        try {
            postExpect(orderId, orderGoodsId, USER_ID, 409, "COMMENT_ALREADY_EXISTS", "该订单行已评价");
            assertThat(findByOrderGoodsId(orderGoodsId)).isNull();
            verify(orderClient, never()).tryCompleteOrder(any(), any());
        } finally {
            cleanup(goodsId);
        }
    }

    @Test
    @DisplayName("CI-36：唯一键撞键（并发重复评价）⇒ 409，绝不 500（05 §5.8）")
    void postDuplicateKey() throws Exception {
        long goodsId = GOODS_ID + 12;
        long orderId = GOODS_ID + 22;
        long orderGoodsId = GOODS_ID + 32;
        // order 侧仍报未评价（并发窗口），但库里已有同一订单行的评价 ⇒ 只能由 uk_comment_order_goods 拦
        stubOrderGoods(orderId, orderGoodsId, goodsId, USER_ID, 301, 1, false);
        Comment existing = new Comment();
        existing.setValueId(goodsId);
        existing.setType(0);
        existing.setOrderId(orderId);
        existing.setOrderGoodsId(orderGoodsId);
        existing.setUserId(USER_ID);
        existing.setStar(5);
        existing.setStatus(0);
        existing.setHasPicture(false);
        existing.setDeleted(false);
        existing.setAddTime(LocalDateTime.now());
        existing.setUpdateTime(LocalDateTime.now());
        commentMapper.insert(existing);
        try {
            postExpect(orderId, orderGoodsId, USER_ID, 409, "COMMENT_ALREADY_EXISTS", "该订单行已评价");
            verify(orderClient, never()).tryCompleteOrder(any(), any());
        } finally {
            cleanup(goodsId);
        }
    }

    @Test
    @DisplayName("CI-36：非本人订单 ⇒ 403 NOT_ORDER_OWNER（不传 userId 给 CN-IN-01）")
    void postNotOwner() throws Exception {
        long goodsId = GOODS_ID + 13;
        long orderId = GOODS_ID + 23;
        long orderGoodsId = GOODS_ID + 33;
        stubOrderGoods(orderId, orderGoodsId, goodsId, USER_ID + 1, 301, 1, false);
        try {
            postExpect(orderId, orderGoodsId, USER_ID, 403, "NOT_ORDER_OWNER", "无权评价他人订单");
            // 05 §4.5：CN-IN-01 调用不传 userId，归属在本地比对
            verify(orderClient).orderGoodsStatus(eq(orderGoodsId), isNull());
            verify(orderClient, never()).tryCompleteOrder(any(), any());
        } finally {
            cleanup(goodsId);
        }
    }

    @Test
    @DisplayName("CI-36：未收货 / 已退款 / 订单号不符 各自返回对应错误码")
    void postRejects() throws Exception {
        long goodsId = GOODS_ID + 14;
        long orderId = GOODS_ID + 24;
        long orderGoodsId = GOODS_ID + 34;
        try {
            stubOrderGoods(orderId, orderGoodsId, goodsId, USER_ID, 201, 1, false);
            postExpect(orderId, orderGoodsId, USER_ID, 400, "ORDER_GOODS_NOT_RECEIVED", "仅已收货订单可评价");

            stubOrderGoods(orderId, orderGoodsId, goodsId, USER_ID, 301, 2, false);
            postExpect(orderId, orderGoodsId, USER_ID, 400, "ORDER_GOODS_REFUNDED", "该商品已退款，不可评价");

            stubOrderGoods(orderId, orderGoodsId, goodsId, USER_ID, 301, 1, false);
            postExpect(orderId + 1, orderGoodsId, USER_ID, 400, "PARAM_INVALID", "订单行不属于该订单");
            verify(orderClient, never()).tryCompleteOrder(any(), any());
        } finally {
            cleanup(goodsId);
        }
    }

    @Test
    @DisplayName("CI-36：订单行不存在（Feign 404）⇒ 404 RESOURCE_NOT_FOUND「订单行不存在」")
    void postOrderGoodsMissing() throws Exception {
        long goodsId = GOODS_ID + 15;
        long orderId = GOODS_ID + 25;
        long orderGoodsId = GOODS_ID + 35;
        FeignException notFound = mock(FeignException.class);
        when(notFound.status()).thenReturn(404);
        when(orderClient.orderGoodsStatus(eq(orderGoodsId), isNull())).thenThrow(notFound);
        try {
            postExpect(orderId, orderGoodsId, USER_ID, 404, "RESOURCE_NOT_FOUND", "订单行不存在");
        } finally {
            cleanup(goodsId);
        }
    }

    @Test
    @DisplayName("CI-36：CN-IN-08 连续失败 ⇒ 仍 200 且 orderStatusUpdated=false，重试 2 次（05 §2.3.4）")
    void postSyncFailureSwallowed() throws Exception {
        long goodsId = GOODS_ID + 16;
        long orderId = GOODS_ID + 26;
        long orderGoodsId = GOODS_ID + 36;
        stubOrderGoods(orderId, orderGoodsId, goodsId, USER_ID, 301, 1, false);
        when(orderClient.tryCompleteOrder(eq(orderId), any()))
                .thenThrow(new IllegalStateException("order 服务不可达"));
        long failuresBefore = goodsLogErrorCounter.localCount();
        try {
            String body = "{\"orderId\":" + orderId + ",\"orderGoodsId\":" + orderGoodsId
                    + ",\"star\":4,\"content\":\"还行\"}";
            mockMvc().perform(post("/app/goods/comment/post")
                            .header("user-info", String.valueOf(USER_ID))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.commentId").exists())
                    .andExpect(jsonPath("$.data.orderStatusUpdated").value(false));
            // 评价已经提交，不能因为订单状态同步失败回滚
            assertThat(findByOrderGoodsId(orderGoodsId)).isNotNull();
            verify(orderClient, times(2)).tryCompleteOrder(eq(orderId), any());
            // 05 §2.3.4 + 裁决 2.9：两次都失败要留「日志异常计数」，不只写 WARN
            assertThat(goodsLogErrorCounter.localCount()).isEqualTo(failuresBefore + 1);
        } finally {
            cleanup(goodsId);
        }
    }

    /* ==================== 夹具 ==================== */

    private void stubOrderGoods(long orderId, long orderGoodsId, long goodsId, long userId,
                                int orderStatus, int refundStatus, boolean commented) {
        OrderGoodsStatusDTO dto = new OrderGoodsStatusDTO();
        dto.setOrderGoodsId(orderGoodsId);
        dto.setOrderId(orderId);
        dto.setUserId(userId);
        dto.setGoodsId(goodsId);
        dto.setOrderStatus(orderStatus);
        dto.setRefundStatus(refundStatus);
        dto.setCommented(commented);
        dto.setCanComment(orderStatus == 301 && refundStatus != 2 && !commented);
        when(orderClient.orderGoodsStatus(eq(orderGoodsId), isNull())).thenReturn(dto);
        when(userClient.listUserByIds(any())).thenReturn(List.of());
    }

    private void postExpect(long orderId, long orderGoodsId, long userId,
                            int code, String errorCode, String msg) throws Exception {
        String body = "{\"orderId\":" + orderId + ",\"orderGoodsId\":" + orderGoodsId
                + ",\"star\":5,\"content\":\"不错\"}";
        mockMvc().perform(post("/app/goods/comment/post")
                        .header("user-info", String.valueOf(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.data.errorCode").value(errorCode))
                .andExpect(jsonPath("$.msg").value(msg));
    }

    private Long insertComment(long goodsId, int star, boolean hasPicture, int status) {
        Comment comment = new Comment();
        comment.setValueId(goodsId);
        comment.setType(0);
        comment.setUserId(USER_ID);
        comment.setStar(star);
        comment.setContent("测试评价-" + star);
        comment.setHasPicture(hasPicture);
        comment.setStatus(status);
        comment.setDeleted(false);
        comment.setAddTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        commentMapper.insert(comment);
        return comment.getId();
    }

    private Comment findByOrderGoodsId(long orderGoodsId) {
        return commentMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Comment>()
                .eq(Comment::getOrderGoodsId, orderGoodsId));
    }

    /** 物理清理本次用例造的评价行（goodsId 是本类独占的编号段） */
    private void cleanup(long goodsId) {
        commentMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Comment>()
                .eq(Comment::getValueId, goodsId));
        // 撞键用例可能用了同一编号段内的 orderGoodsId
        commentMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Comment>()
                .ge(Comment::getOrderGoodsId, goodsId + 30)
                .le(Comment::getOrderGoodsId, goodsId + 40));
    }
}
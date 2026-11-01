package com.litemall.controller;

import com.litemall.domain.DTO.CommentAdminQuery;
import com.litemall.domain.PageVO;
import com.litemall.domain.dto.CommentReplyDTO;
import com.litemall.domain.dto.CommentReplyResultDTO;
import com.litemall.domain.dto.CommentStatusDTO;
import com.litemall.domain.dto.CommentStatusResultDTO;
import com.litemall.service.ICommentService;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R2-04 管理端评价内部接口接线测试（05 §3.9~§3.11 对内三端点）。
 *
 * <p>
 * 验证映射、内部密钥保护与入参透传；业务规则见
 * {@code CommentAdminServiceTest}（真实 MySQL）与 {@code CommentAdminQueryTest}（纯单元）。
 * 对内接口保留裸 DTO + 非 200（05 §1.1），因此这里断言的是 HTTP 层而非 {@code Result}。
 * </p>
 */
@SpringBootTest(properties = "hm.internal.token=test-inner-secret")
class CommentAdminInnerApiTest {

    private static final String TOKEN = "test-inner-secret";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ICommentService commentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("CN-29：9 个入参原样映射为 CommentAdminQuery（归一在服务侧）")
    void adminCommentList() throws Exception {
        when(commentService.adminListComments(any())).thenReturn(new PageVO<>(0L, 0L, List.of()));

        mockMvc.perform(get("/inner/goods/admin/comment/list")
                        .header("X-Internal-Token", TOKEN)
                        .param("pageNum", "2")
                        .param("pageSize", "10")
                        .param("goodsId", "1001")
                        .param("keyword", "好")
                        .param("star", "1,5")
                        .param("replied", "true")
                        .param("status", "0,1")
                        .param("dateFrom", "2026-01-01 00:00:00")
                        .param("dateTo", "2026-12-31 23:59:59"))
                .andExpect(status().isOk());

        ArgumentCaptor<CommentAdminQuery> captor = ArgumentCaptor.forClass(CommentAdminQuery.class);
        verify(commentService).adminListComments(captor.capture());
        CommentAdminQuery query = captor.getValue();
        assertThat(query.getPageNum()).isEqualTo(2);
        assertThat(query.getPageSize()).isEqualTo(10);
        assertThat(query.getGoodsId()).isEqualTo(1001L);
        assertThat(query.getKeyword()).isEqualTo("好");
        assertThat(query.getStar()).isEqualTo("1,5");
        assertThat(query.getReplied()).isTrue();
        assertThat(query.getStatus()).isEqualTo("0,1");
        assertThat(query.getDateFrom()).isEqualTo("2026-01-01 00:00:00");
        assertThat(query.getDateTo()).isEqualTo("2026-12-31 23:59:59");
    }

    @Test
    @DisplayName("CN-29：缺少内部密钥 ⇒ 非 200（H3 拦截器）")
    void adminCommentListRequiresToken() throws Exception {
        mockMvc.perform(get("/inner/goods/admin/comment/list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CN-30：回复评价透传 commentId + content 并归还 service 结果")
    void adminCommentReply() throws Exception {
        when(commentService.replyComment(any()))
                .thenReturn(CommentReplyResultDTO.of(7001L, "已收到", "2026-11-01 10:00:00"));

        mockMvc.perform(post("/inner/goods/admin/comment/reply")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commentId\":7001,\"content\":\"已收到\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentId").value(7001))
                .andExpect(jsonPath("$.adminContent").value("已收到"));

        ArgumentCaptor<CommentReplyDTO> captor = ArgumentCaptor.forClass(CommentReplyDTO.class);
        verify(commentService).replyComment(captor.capture());
        assertThat(captor.getValue().getCommentId()).isEqualTo(7001L);
        assertThat(captor.getValue().getContent()).isEqualTo("已收到");
    }

    @Test
    @DisplayName("CN-31：隐藏/恢复透传目标态并归还 service 结果")
    void adminCommentStatus() throws Exception {
        when(commentService.updateCommentStatus(any()))
                .thenReturn(CommentStatusResultDTO.of(7002L, 1, "已隐藏"));

        mockMvc.perform(post("/inner/goods/admin/comment/status")
                        .header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commentId\":7002,\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commentId").value(7002))
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.statusText").value("已隐藏"));

        ArgumentCaptor<CommentStatusDTO> captor = ArgumentCaptor.forClass(CommentStatusDTO.class);
        verify(commentService).updateCommentStatus(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }
}
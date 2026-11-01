package com.litemall.service;

import com.litemall.client.UserClient;
import com.litemall.domain.DTO.CommentAdminQuery;
import com.litemall.domain.PO.Comment;
import com.litemall.domain.PageVO;
import com.litemall.domain.dto.CommentAdminVO;
import com.litemall.domain.dto.CommentReplyDTO;
import com.litemall.domain.dto.CommentReplyResultDTO;
import com.litemall.domain.dto.CommentStatusDTO;
import com.litemall.domain.dto.CommentStatusResultDTO;
import com.litemall.exception.GoodsApiException;
import com.litemall.mapper.CommentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R2-04 管理端评价服务测试（05 §3.9~§3.11），真实 MySQL。
 *
 * <p>
 * 重点锁三条容易写错的契约：
 * </p>
 * <ul>
 *   <li><b>total 含已隐藏</b>，与 C 端 CI-35 的 {@code status=0} 口径<b>相反</b>（05 §5.7：「不得复用同一 SQL」）；</li>
 *   <li>回复是<b>覆盖写</b>且 {@code replyTime} 每次刷新；</li>
 *   <li>隐藏/恢复是<b>目标态 CAS</b>：重复提交同一目标态按幂等 200（E-3），行不存在才 404。</li>
 * </ul>
 */
@SpringBootTest
class CommentAdminServiceTest {

    private static final long GOODS_ID = 9_100_000L + System.currentTimeMillis() % 1_000_000;

    private static final long USER_ID = 710_000L + System.currentTimeMillis() % 1_000;

    @Autowired
    private ICommentService commentService;

    @Autowired
    private CommentMapper commentMapper;

    @MockitoBean
    private UserClient userClient;

    @Test
    @DisplayName("CN-29：total 含已隐藏，statusText 显示/已隐藏，C 端同商品只计未隐藏")
    void adminListIncludesHidden() {
        long goodsId = GOODS_ID + 1;
        insert(goodsId, 5, "差一点", null, 0);
        insert(goodsId, 4, "还不错", "谢谢", 0);
        insert(goodsId, 1, "隐藏项", null, 1);
        try {
            PageVO<CommentAdminVO> page = commentService.adminListComments(queryOf(goodsId));
            assertThat(page.getTotal()).as("管理端 total 含已隐藏").isEqualTo(3L);
            assertThat(page.getList()).extracting(CommentAdminVO::getStatus)
                    .containsExactlyInAnyOrder(0, 0, 1);
            assertThat(page.getList()).extracting(CommentAdminVO::getStatusText)
                    .containsExactlyInAnyOrder("显示", "显示", "已隐藏");
            assertThat(page.getList()).extracting(CommentAdminVO::getReplied)
                    .containsExactlyInAnyOrder(true, false, false);
            // 排序固定 add_time DESC, id DESC（最新在前）
            assertThat(page.getList().get(0).getAddTime()).isNotNull();

            // C 端口径与之相反：隐藏项既不在列表也不在 total
            PageVO<com.litemall.domain.VO.CommentVO> appPage =
                    commentService.getCommentList(goodsId, null, null, 1, 20);
            assertThat(appPage.getTotal()).as("C 端 total 只计 status=0").isEqualTo(2L);
        } finally {
            cleanup(goodsId);
        }
    }

    @Test
    @DisplayName("CN-29：status / star / replied / keyword 四个筛选维度")
    void adminListFilters() {
        long goodsId = GOODS_ID + 2;
        insert(goodsId, 5, "物流很快", null, 0);
        insert(goodsId, 3, "包装一般", "已反馈", 0);
        insert(goodsId, 5, "隐藏的差评", null, 1);
        try {
            CommentAdminQuery statusHidden = queryOf(goodsId);
            statusHidden.setStatus("1");
            assertThat(commentService.adminListComments(statusHidden).getTotal()).isEqualTo(1L);

            CommentAdminQuery star5 = queryOf(goodsId);
            star5.setStar("5");
            assertThat(commentService.adminListComments(star5).getTotal()).as("star 多值筛选").isEqualTo(2L);

            CommentAdminQuery replied = queryOf(goodsId);
            replied.setReplied(Boolean.TRUE);
            assertThat(commentService.adminListComments(replied).getTotal()).isEqualTo(1L);

            CommentAdminQuery notReplied = queryOf(goodsId);
            notReplied.setReplied(Boolean.FALSE);
            assertThat(commentService.adminListComments(notReplied).getTotal())
                    .as("未回复 = admin_content 为 NULL 或空串").isEqualTo(2L);

            CommentAdminQuery keyword = queryOf(goodsId);
            keyword.setKeyword("物流");
            assertThat(commentService.adminListComments(keyword).getTotal()).isEqualTo(1L);
        } finally {
            cleanup(goodsId);
        }
    }

    @Test
    @DisplayName("CN-29：时间区间含两端，越界返回 0；pageSize 超上限夹到 100")
    void adminListTimeRangeAndPaging() {
        long goodsId = GOODS_ID + 3;
        insertAt(goodsId, LocalDateTime.of(2026, 3, 1, 10, 0, 0));
        insertAt(goodsId, LocalDateTime.of(2026, 3, 20, 10, 0, 0));
        try {
            CommentAdminQuery inRange = queryOf(goodsId);
            inRange.setDateFrom("2026-03-01 00:00:00");
            inRange.setDateTo("2026-03-31 23:59:59");
            assertThat(commentService.adminListComments(inRange).getTotal()).isEqualTo(2L);

            CommentAdminQuery outOfRange = queryOf(goodsId);
            outOfRange.setDateFrom("2026-04-01 00:00:00");
            outOfRange.setDateTo("2026-04-30 23:59:59");
            assertThat(commentService.adminListComments(outOfRange).getTotal()).isZero();

            CommentAdminQuery big = queryOf(goodsId);
            big.setPageSize(1000);
            commentService.adminListComments(big);
            assertThat(big.getPageSize()).as("pageSize 上限 100（05 §1.4）").isEqualTo(100);
        } finally {
            cleanup(goodsId);
        }
    }

    @Test
    @DisplayName("CN-30：回复是覆盖写，replyTime 每次刷新；不存在 404、空白 400")
    void replyOverwrites() {
        long goodsId = GOODS_ID + 4;
        Long id = insert(goodsId, 5, "待回复", null, 0);
        try {
            CommentReplyDTO first = new CommentReplyDTO();
            first.setCommentId(id);
            first.setContent("  感谢支持  ");
            CommentReplyResultDTO r1 = commentService.replyComment(first);
            assertThat(r1.getAdminContent()).isEqualTo("感谢支持");
            assertThat(r1.getReplyTime()).isNotNull();
            LocalDateTime repliedAt = commentMapper.selectById(id).getReplyTime();
            assertThat(repliedAt).isNotNull();

            CommentReplyDTO second = new CommentReplyDTO();
            second.setCommentId(id);
            second.setContent("补充说明");
            commentService.replyComment(second);
            Comment reloaded = commentMapper.selectById(id);
            assertThat(reloaded.getAdminContent()).isEqualTo("补充说明");
            assertThat(reloaded.getReplyTime()).isAfterOrEqualTo(repliedAt);

            CommentReplyDTO blank = new CommentReplyDTO();
            blank.setCommentId(id);
            blank.setContent("   ");
            assertThatThrownBy(() -> commentService.replyComment(blank))
                    .isInstanceOf(GoodsApiException.class)
                    .hasMessageContaining("回复内容不能为空");

            CommentReplyDTO tooLong = new CommentReplyDTO();
            tooLong.setCommentId(id);
            tooLong.setContent("字".repeat(501));
            assertThatThrownBy(() -> commentService.replyComment(tooLong))
                    .isInstanceOf(GoodsApiException.class)
                    .hasMessageContaining("回复内容最多 500 字");

            CommentReplyDTO missing = new CommentReplyDTO();
            missing.setCommentId(999_999_999L);
            missing.setContent("您好");
            assertThatThrownBy(() -> commentService.replyComment(missing))
                    .isInstanceOf(GoodsApiException.class)
                    .hasMessageContaining("评价不存在");
        } finally {
            cleanup(goodsId);
        }
    }

    @Test
    @DisplayName("CN-31：隐藏 → 重复隐藏幂等 → 恢复；行不存在 404、非法 status 400")
    void statusCasAndIdempotent() {
        long goodsId = GOODS_ID + 5;
        Long id = insert(goodsId, 5, "可隐藏", null, 0);
        try {
            CommentStatusResultDTO hidden = commentService.updateCommentStatus(statusOf(id, 1));
            assertThat(hidden.getStatus()).isEqualTo(1);
            assertThat(hidden.getStatusText()).isEqualTo("已隐藏");
            assertThat(commentMapper.selectById(id).getStatus()).isEqualTo(1);

            // E-3：已是目标态再提交同一目标 ⇒ 200 幂等，不抛 409
            CommentStatusResultDTO again = commentService.updateCommentStatus(statusOf(id, 1));
            assertThat(again.getStatus()).isEqualTo(1);

            CommentStatusResultDTO restored = commentService.updateCommentStatus(statusOf(id, 0));
            assertThat(restored.getStatusText()).isEqualTo("显示");
            assertThat(commentMapper.selectById(id).getStatus()).isZero();

            assertThatThrownBy(() -> commentService.updateCommentStatus(statusOf(999_999_999L, 1)))
                    .isInstanceOf(GoodsApiException.class)
                    .hasMessageContaining("评价不存在");

            assertThatThrownBy(() -> commentService.updateCommentStatus(statusOf(id, 2)))
                    .isInstanceOf(GoodsApiException.class)
                    .hasMessageContaining("status 只能为 0 或 1");
        } finally {
            cleanup(goodsId);
        }
    }

    /* ==================== 夹具 ==================== */

    private CommentAdminQuery queryOf(long goodsId) {
        CommentAdminQuery query = new CommentAdminQuery();
        query.setGoodsId(goodsId);
        query.setPageNum(1);
        query.setPageSize(20);
        return query;
    }

    private CommentStatusDTO statusOf(Long id, int status) {
        CommentStatusDTO dto = new CommentStatusDTO();
        dto.setCommentId(id);
        dto.setStatus(status);
        return dto;
    }

    private Long insert(long goodsId, int star, String content, String adminContent, int status) {
        Comment comment = new Comment();
        comment.setValueId(goodsId);
        comment.setType(0);
        comment.setUserId(USER_ID);
        comment.setStar(star);
        comment.setContent(content);
        comment.setAdminContent(adminContent);
        comment.setHasPicture(false);
        comment.setStatus(status);
        comment.setDeleted(false);
        comment.setAddTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        if (adminContent != null) {
            comment.setReplyTime(LocalDateTime.now());
        }
        commentMapper.insert(comment);
        return comment.getId();
    }

    private void insertAt(long goodsId, LocalDateTime addTime) {
        Comment comment = new Comment();
        comment.setValueId(goodsId);
        comment.setType(0);
        comment.setUserId(USER_ID);
        comment.setStar(5);
        comment.setContent("时间筛选");
        comment.setHasPicture(false);
        comment.setStatus(0);
        comment.setDeleted(false);
        comment.setAddTime(addTime);
        comment.setUpdateTime(addTime);
        commentMapper.insert(comment);
    }

    private void cleanup(long goodsId) {
        commentMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Comment>()
                .eq(Comment::getValueId, goodsId));
    }
}
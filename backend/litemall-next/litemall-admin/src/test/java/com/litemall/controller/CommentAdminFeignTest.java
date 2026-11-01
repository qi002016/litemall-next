package com.litemall.controller;

import com.litemall.client.GoodsClient;
import com.litemall.domain.DTO.CommentReplyReqDTO;
import com.litemall.domain.DTO.CommentStatusReqDTO;
import com.litemall.domain.PageVO;
import com.litemall.domain.Result;
import com.litemall.domain.dto.CommentAdminVO;
import com.litemall.domain.dto.CommentReplyDTO;
import com.litemall.domain.dto.CommentReplyResultDTO;
import com.litemall.domain.dto.CommentStatusDTO;
import com.litemall.domain.dto.CommentStatusResultDTO;
import com.litemall.exception.BadRequestException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R2-04 管理端评价接口的转发级测试（05 §3.9~§3.11，admin 只归一 + 转发）。
 *
 * <p>
 * 用 Mockito 直接 new 控制器，避开 HTTP 层，专测「归一后的值是否准」与
 * 「明显非法是否在 admin 就被 400 拦住（不打到 goods）」。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class CommentAdminFeignTest {

    @Mock
    private GoodsClient goodsClient;

    private CommentController controller;

    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @AfterAll
    static void closeValidator() {
        if (validator instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // 关闭失败不影响断言
            }
        }
        validator = null;
    }

    @BeforeEach
    void setUp() {
        controller = new CommentController(goodsClient);
    }

    @Test
    @DisplayName("CN-29：分页/关键字/多值/时间全部归一后转发")
    void listNormalizes() {
        when(goodsClient.adminCommentList(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageVO<>(0L, 0L, List.of()));

        Result<PageVO<CommentAdminVO>> result = controller.list(
                null, 1000, 1001L, "  物流  ", "1, 5", Boolean.TRUE, "0,1",
                "2026-01-01 00:00:00", "2026-12-31 23:59:59");

        assertThat(result.getCode()).isEqualTo(200);
        verify(goodsClient).adminCommentList(1, 100, 1001L, "物流", "1,5", Boolean.TRUE, "0,1",
                "2026-01-01 00:00:00", "2026-12-31 23:59:59");
    }

    @Test
    @DisplayName("CN-29：star 越界、关键字超长、时间格式错都在 admin 侧 400，不打到 goods")
    void listRejectsInvalid() {
        assertThatThrownBy(() -> controller.list(null, null, null, null, "9", null, null, null, null))
                .isInstanceOfSatisfying(BadRequestException.class, e -> {
                    assertThat(e.getMessage()).contains("star 取值非法");
                    assertThat(e.getErrorCode()).isEqualTo("PARAM_INVALID");
                });

        assertThatThrownBy(() -> controller.list(null, null, null, "字".repeat(51), null, null, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("keyword 长度不能超过 50 位");

        assertThatThrownBy(() -> controller.list(null, null, null, null, null, null, null,
                "2026-01-01", null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dateFrom 格式应为 yyyy-MM-dd HH:mm:ss");

        verify(goodsClient, never()).adminCommentList(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CN-30：回复内容去空白后转发，结果原样返回")
    void replyTrimsAndForwards() {
        when(goodsClient.adminCommentReply(any()))
                .thenReturn(CommentReplyResultDTO.of(7001L, "感谢支持", "2026-11-01 10:00:00"));

        CommentReplyReqDTO body = new CommentReplyReqDTO();
        body.setCommentId(7001L);
        body.setContent("  感谢支持  ");

        Result<CommentReplyResultDTO> result = controller.reply(body);
        assertThat(result.getData().getAdminContent()).isEqualTo("感谢支持");

        ArgumentCaptor<CommentReplyDTO> captor = ArgumentCaptor.forClass(CommentReplyDTO.class);
        verify(goodsClient).adminCommentReply(captor.capture());
        assertThat(captor.getValue().getCommentId()).isEqualTo(7001L);
        assertThat(captor.getValue().getContent()).isEqualTo("感谢支持");
    }

    @Test
    @DisplayName("CN-30：纯空白回复 400 且不打到 goods；超 500 字由 JSR-303 拦")
    void replyRejectsBlank() {
        CommentReplyReqDTO blank = new CommentReplyReqDTO();
        blank.setCommentId(7001L);
        blank.setContent("   ");
        assertThatThrownBy(() -> controller.reply(blank))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("回复内容不能为空");

        CommentReplyReqDTO tooLong = new CommentReplyReqDTO();
        tooLong.setCommentId(7001L);
        tooLong.setContent("字".repeat(501));
        Set<ConstraintViolation<CommentReplyReqDTO>> violations = validator.validate(tooLong);
        assertThat(violations).extracting(v -> v.getMessage()).contains("回复内容最多 500 字");

        verify(goodsClient, never()).adminCommentReply(any());
    }

    @Test
    @DisplayName("CN-31：目标态原样转发，结果原样返回；status 只能是 0/1")
    void statusForwards() {
        when(goodsClient.adminCommentStatus(any()))
                .thenReturn(CommentStatusResultDTO.of(7002L, 1, "已隐藏"));

        CommentStatusReqDTO body = new CommentStatusReqDTO();
        body.setCommentId(7002L);
        body.setStatus(1);

        Result<CommentStatusResultDTO> result = controller.status(body);
        assertThat(result.getData().getStatusText()).isEqualTo("已隐藏");

        ArgumentCaptor<CommentStatusDTO> captor = ArgumentCaptor.forClass(CommentStatusDTO.class);
        verify(goodsClient).adminCommentStatus(captor.capture());
        assertThat(captor.getValue().getCommentId()).isEqualTo(7002L);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);

        CommentStatusReqDTO invalid = new CommentStatusReqDTO();
        invalid.setCommentId(7002L);
        invalid.setStatus(2);
        assertThat(validator.validate(invalid)).extracting(v -> v.getMessage())
                .contains("评价状态只能是 0（显示）或 1（已隐藏）");
    }
}
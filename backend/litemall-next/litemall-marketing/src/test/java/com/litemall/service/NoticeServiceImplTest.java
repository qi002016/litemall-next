package com.litemall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.PO.Notice;
import com.litemall.domain.dto.NoticeAdminDTO;
import com.litemall.domain.dto.NoticeDTO;
import com.litemall.domain.PageVO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ConflictException;
import com.litemall.exception.NotFoundException;
import com.litemall.mapper.NoticeMapper;
import com.litemall.service.impl.NoticeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p>
 * 公告服务单元测试（CN-34/35/36 契约级自检）。
 * </p>
 * <p>
 * 覆盖 05 §3.12–§3.14：新建不得带 id、标题/内容边界、编辑目标态幂等、
 * CAS 冲突 409 {@code CONCURRENT_MODIFIED}、关键字长度、列表 adminName 恒 null。
 * </p>
 *
 * @since 2026-11-01
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NoticeServiceImplTest {

    @Mock
    private NoticeMapper noticeMapper;

    private NoticeServiceImpl noticeService;

    @BeforeEach
    void setUp() {
        noticeService = new NoticeServiceImpl();
        ReflectionTestUtils.setField(noticeService, "baseMapper", noticeMapper);
    }

    // ------------------------------------------------------------ CN-35 新建

    @Test
    @DisplayName("CN-35：新建成功 → 返回新 id，enabled 缺省 true，标题去空格，adminId 取透传值")
    void createNotice_ok() {
        when(noticeMapper.insert(any(Notice.class))).thenAnswer(invocation -> {
            Notice n = invocation.getArgument(0);
            n.setId(301L);
            return 1;
        });
        NoticeDTO dto = new NoticeDTO();
        dto.setTitle("  系统维护通知  ");
        dto.setContent("今晚 22:00 停机维护");
        dto.setAdminId(9L);

        Long id = noticeService.createNotice(dto);

        assertEquals(301L, id);
        ArgumentCaptor<Notice> captor = ArgumentCaptor.forClass(Notice.class);
        verify(noticeMapper).insert(captor.capture());
        Notice saved = captor.getValue();
        assertEquals("系统维护通知", saved.getTitle());
        assertEquals(Boolean.TRUE, saved.getEnabled());
        assertEquals(9L, saved.getAdminId());
        assertEquals(Boolean.FALSE, saved.getDeleted());
    }

    @Test
    @DisplayName("CN-35：body 带 id → 400 PARAM_INVALID（不得走成更新）")
    void createNotice_withId_rejected() {
        NoticeDTO dto = new NoticeDTO();
        dto.setId(301L);
        dto.setTitle("标题");
        dto.setContent("正文");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> noticeService.createNotice(dto));

        assertEquals("PARAM_INVALID", ex.getErrorCode());
        verify(noticeMapper, never()).insert(any(Notice.class));
    }

    @Test
    @DisplayName("CN-35：标题为空 / 超 100 字、内容为空 / 超 2000 字 → 400 PARAM_INVALID")
    void createNotice_fieldBounds_rejected() {
        NoticeDTO blankTitle = new NoticeDTO();
        blankTitle.setTitle("   ");
        blankTitle.setContent("正文");
        assertEquals("PARAM_INVALID",
                assertThrows(BadRequestException.class, () -> noticeService.createNotice(blankTitle)).getErrorCode());

        NoticeDTO longTitle = new NoticeDTO();
        longTitle.setTitle("标".repeat(101));
        longTitle.setContent("正文");
        assertEquals("PARAM_INVALID",
                assertThrows(BadRequestException.class, () -> noticeService.createNotice(longTitle)).getErrorCode());

        NoticeDTO blankContent = new NoticeDTO();
        blankContent.setTitle("标题");
        blankContent.setContent("");
        assertEquals("PARAM_INVALID",
                assertThrows(BadRequestException.class, () -> noticeService.createNotice(blankContent)).getErrorCode());

        NoticeDTO longContent = new NoticeDTO();
        longContent.setTitle("标题");
        longContent.setContent("正".repeat(2001));
        assertEquals("PARAM_INVALID",
                assertThrows(BadRequestException.class, () -> noticeService.createNotice(longContent)).getErrorCode());

        verify(noticeMapper, never()).insert(any(Notice.class));
    }

    // ------------------------------------------------------------ CN-36 编辑

    @Test
    @DisplayName("CN-36：id 不存在或已软删 → 404 RESOURCE_NOT_FOUND")
    void updateNotice_notFound() {
        when(noticeMapper.selectById(404L)).thenReturn(null);

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> noticeService.updateNotice(updateDto(404L, "新标题")));

        assertEquals("RESOURCE_NOT_FOUND", ex.getErrorCode());

        Notice deleted = notice(405L).setDeleted(true);
        when(noticeMapper.selectById(405L)).thenReturn(deleted);
        assertThrows(NotFoundException.class, () -> noticeService.updateNotice(updateDto(405L, "新标题")));
    }

    @Test
    @DisplayName("CN-36：目标态幂等 — 重复提交同一目标值直接成功，不做 CAS")
    void updateNotice_sameTargetState_isIdempotent() {
        Notice current = notice(301L);
        when(noticeMapper.selectById(301L)).thenReturn(current);

        Long id = noticeService.updateNotice(updateDto(301L, "公告标题"));

        assertEquals(301L, id);
        verify(noticeMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("CN-36：启停（enabled=false）走 CAS 更新，成功返回 id")
    void updateNotice_disable_updatesByCas() {
        Notice current = notice(301L).setEnabled(true);
        when(noticeMapper.selectById(301L)).thenReturn(current);
        when(noticeMapper.update(isNull(), any())).thenReturn(1);
        NoticeDTO dto = new NoticeDTO();
        dto.setId(301L);
        dto.setEnabled(false);

        Long id = noticeService.updateNotice(dto);

        assertEquals(301L, id);
        verify(noticeMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("CN-36：CAS affected=0 → 409 CONCURRENT_MODIFIED + 统一文案")
    void updateNotice_casConflict() {
        Notice current = notice(301L);
        when(noticeMapper.selectById(301L)).thenReturn(current);
        when(noticeMapper.update(isNull(), any())).thenReturn(0);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> noticeService.updateNotice(updateDto(301L, "别人先改过的标题")));

        assertEquals("CONCURRENT_MODIFIED", ex.getErrorCode());
        assertEquals("内容已被他人修改，请刷新后重试", ex.getMessage());
    }

    @Test
    @DisplayName("CN-36：id 缺失 → 400 PARAM_INVALID")
    void updateNotice_missingId_rejected() {
        NoticeDTO dto = new NoticeDTO();
        dto.setTitle("新标题");

        assertEquals("PARAM_INVALID",
                assertThrows(BadRequestException.class, () -> noticeService.updateNotice(dto)).getErrorCode());
    }

    // ------------------------------------------------------------ CN-34 列表

    @Test
    @DisplayName("CN-34：列表项映射 NoticeAdminDTO — 无 sortOrder、adminName 恒 null（marketing 不读 admin 表）")
    void toAdminDTO_mapsWithoutSortOrderAndAdminName() {
        NoticeAdminDTO dto = ReflectionTestUtils.invokeMethod(noticeService, "toAdminDTO", notice(301L));

        assertNotNull(dto);
        assertEquals(301L, dto.getId());
        assertEquals("公告标题", dto.getTitle());
        assertEquals("公告正文", dto.getContent());
        assertEquals(Boolean.TRUE, dto.getEnabled());
        assertEquals(9L, dto.getAdminId());
        assertNull(dto.getAdminName());
    }

    @Test
    @DisplayName("CN-34：keyword 超过 50 字 → 400 PARAM_INVALID")
    void getAdminNoticeList_keywordTooLong_rejected() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> noticeService.getAdminNoticeList(1, 10, "关".repeat(51), null));

        assertEquals("PARAM_INVALID", ex.getErrorCode());
        verify(noticeMapper, never()).selectPage(any(), any());
    }

    // ---------------------------------------------------------------- helpers

    private Notice notice(Long id) {
        return new Notice()
                .setId(id)
                .setTitle("公告标题")
                .setContent("公告正文")
                .setEnabled(true)
                .setAdminId(9L)
                .setAddTime(LocalDateTime.now().minusDays(1))
                .setUpdateTime(LocalDateTime.now().minusHours(1))
                .setDeleted(false);
    }

    private NoticeDTO updateDto(Long id, String title) {
        NoticeDTO dto = new NoticeDTO();
        dto.setId(id);
        dto.setTitle(title);
        return dto;
    }
}
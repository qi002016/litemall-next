package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.PageVO;
import com.litemall.domain.PO.Notice;
import com.litemall.domain.VO.NoticeVO;
import com.litemall.domain.dto.NoticeAdminDTO;
import com.litemall.domain.dto.NoticeDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ConflictException;
import com.litemall.exception.NotFoundException;
import com.litemall.mapper.NoticeMapper;
import com.litemall.service.INoticeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 通知公告服务实现。C 端只读（CI-59），管理端写入口在 marketing 内部接口（CN-IN-05/06/07）。
 * </p>
 * <p>
 * R2 依据 05 §3.12–§3.14 与 §5.13：
 * </p>
 * <ul>
 *   <li>公告<b>无</b> {@code sort_order} 列，列表排序固定 {@code add_time DESC, id DESC}，不提供 sortOrder；</li>
 *   <li>C 端只返回 {@code enabled=1}（BR-CMS-04：停用即刻从 CI-59 消失）；</li>
 *   <li>编辑 CAS 于 {@code id + update_time}，{@code affected=0} ⇒ 409 {@code CONCURRENT_MODIFIED}；
 *       目标态幂等（重复提交同一目标值直接成功）；</li>
 *   <li>公告不提供删除（08 已定），停用即 {@code enabled=false}。</li>
 * </ul>
 *
 * @author qihuang
 * @since 2026-09-23
 */
@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements INoticeService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 公告标题长度上限（05 §3.13） */
    private static final int TITLE_MAX = 100;
    /** 公告正文长度上限（05 §3.13，纯文本） */
    private static final int CONTENT_MAX = 2000;

    @Override
    public PageVO<NoticeVO> getNoticeList(int pageNum, int pageSize) {
        Page<Notice> page = new Page<>(pageNum, pageSize);
        // BR-CMS-04：C 端只返回启用中的公告，停用/新建未启用的即刻不可见
        Page<Notice> p = lambdaQuery()
                .eq(Notice::getDeleted, false)
                .eq(Notice::getEnabled, true)
                .orderByDesc(Notice::getAddTime)
                .orderByDesc(Notice::getId)
                .page(page);
        List<NoticeVO> voList = p.getRecords().stream().map(n -> {
            NoticeVO vo = new NoticeVO();
            vo.setId(n.getId());
            vo.setTitle(n.getTitle());
            vo.setContent(n.getContent());
            vo.setAddTime(n.getAddTime() == null ? null : n.getAddTime().format(DT));
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(p.getTotal(), p.getPages(), voList);
    }

    @Override
    public PageVO<NoticeAdminDTO> getAdminNoticeList(int pageNum, int pageSize, String keyword, Boolean enabled) {
        String kw = normalizeKeyword(keyword);
        Page<Notice> p = lambdaQuery()
                .eq(Notice::getDeleted, false)
                .like(StringUtils.hasText(kw), Notice::getTitle, kw)
                .eq(enabled != null, Notice::getEnabled, enabled)
                .orderByDesc(Notice::getAddTime)
                .orderByDesc(Notice::getId)
                .page(new Page<>(pageNum, pageSize));
        // 空列表由 PageVO 承载（records 为 [] 而非 null，05 §1.5.6）
        return PageVO.of(p, this::toAdminDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNotice(NoticeDTO dto) {
        if (dto == null) {
            throw new BadRequestException("公告内容不能为空", BadRequestException.PARAM_INVALID);
        }
        if (dto.getId() != null) {
            // 05 §3.13：新建不得传 id
            throw new BadRequestException("新建公告不得指定ID", BadRequestException.PARAM_INVALID);
        }
        String title = requireTitle(dto.getTitle());
        String content = requireContent(dto.getContent());
        LocalDateTime now = LocalDateTime.now();
        Notice notice = new Notice()
                .setTitle(title)
                .setContent(content)
                .setEnabled(dto.getEnabled() == null ? Boolean.TRUE : dto.getEnabled())
                .setAdminId(dto.getAdminId())
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(Boolean.FALSE);
        save(notice);
        return notice.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateNotice(NoticeDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BadRequestException("公告ID不能为空", BadRequestException.PARAM_INVALID);
        }
        Notice exists = getById(dto.getId());
        if (exists == null || Boolean.TRUE.equals(exists.getDeleted())) {
            throw new NotFoundException("公告不存在");
        }

        // 未传的字段保持原值（05 §3.14）
        String title = dto.getTitle() == null ? null : requireTitle(dto.getTitle());
        String content = dto.getContent() == null ? null : requireContent(dto.getContent());
        Boolean enabled = dto.getEnabled();

        boolean titleSame = title == null || title.equals(exists.getTitle());
        boolean contentSame = content == null || content.equals(exists.getContent());
        boolean enabledSame = enabled == null || enabled.equals(exists.getEnabled());
        if (titleSame && contentSame && enabledSame) {
            // 目标态幂等：重复提交同一目标值直接成功，不做 CAS（否则会把自己的上一次提交判成并发冲突）
            return exists.getId();
        }

        LocalDateTime baseUpdateTime = dto.getUpdateTime() == null ? exists.getUpdateTime() : dto.getUpdateTime();
        LambdaUpdateWrapper<Notice> wrapper = new LambdaUpdateWrapper<Notice>()
                .eq(Notice::getId, exists.getId())
                .eq(Notice::getDeleted, false)
                .set(Notice::getUpdateTime, LocalDateTime.now());
        if (baseUpdateTime != null) {
            // CAS：以读取到的 update_time 为基准，他人在此期间改过则 affected=0
            wrapper.eq(Notice::getUpdateTime, baseUpdateTime);
        }
        if (title != null) {
            wrapper.set(Notice::getTitle, title);
        }
        if (content != null) {
            wrapper.set(Notice::getContent, content);
        }
        if (enabled != null) {
            wrapper.set(Notice::getEnabled, enabled);
        }
        if (!update(wrapper)) {
            throw new ConflictException("内容已被他人修改，请刷新后重试", "CONCURRENT_MODIFIED");
        }
        return exists.getId();
    }

    private String requireTitle(String raw) {
        String title = raw == null ? null : raw.trim();
        if (!StringUtils.hasText(title)) {
            throw new BadRequestException("公告标题不能为空", BadRequestException.PARAM_INVALID);
        }
        if (title.length() > TITLE_MAX) {
            throw new BadRequestException("公告标题不能超过" + TITLE_MAX + "个字符",
                    BadRequestException.PARAM_INVALID);
        }
        return title;
    }

    private String requireContent(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BadRequestException("公告内容不能为空", BadRequestException.PARAM_INVALID);
        }
        if (raw.length() > CONTENT_MAX) {
            throw new BadRequestException("公告内容不能超过" + CONTENT_MAX + "个字符",
                    BadRequestException.PARAM_INVALID);
        }
        return raw;
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String kw = keyword.trim();
        if (kw.length() > INoticeService.KEYWORD_MAX) {
            throw new BadRequestException("关键字长度不能超过" + INoticeService.KEYWORD_MAX + "个字符",
                    BadRequestException.PARAM_INVALID);
        }
        return kw;
    }

    private NoticeAdminDTO toAdminDTO(Notice notice) {
        NoticeAdminDTO dto = new NoticeAdminDTO();
        dto.setId(notice.getId());
        dto.setTitle(notice.getTitle());
        dto.setContent(notice.getContent());
        dto.setEnabled(notice.getEnabled());
        dto.setAdminId(notice.getAdminId());
        // marketing 无法读 admin 表：不编造创建人名（05 §3.12）
        dto.setAdminName(null);
        dto.setAddTime(notice.getAddTime());
        dto.setUpdateTime(notice.getUpdateTime());
        return dto;
    }
}
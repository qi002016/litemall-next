package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PageVO;
import com.litemall.domain.PO.Notice;
import com.litemall.domain.VO.NoticeVO;
import com.litemall.domain.dto.NoticeAdminDTO;
import com.litemall.domain.dto.NoticeDTO;

public interface INoticeService extends IService<Notice> {

    /** 公告标题关键字上限（05 §3.12） */
    int KEYWORD_MAX = 50;

    /**
     * C 端公告列表（CI-59，只返回 enabled=1，排序 add_time DESC, id DESC）
     */
    PageVO<NoticeVO> getNoticeList(int pageNum, int pageSize);

    /**
     * 管理端公告列表（CN-IN-05 / CN-34）。
     *
     * @param pageNum  页码（≥1）
     * @param pageSize 每页条数
     * @param keyword  标题模糊关键字（≤50）
     * @param enabled  启用状态筛选（null 为不限）
     * @return 分页公告（adminName 恒 null）
     */
    PageVO<NoticeAdminDTO> getAdminNoticeList(int pageNum, int pageSize, String keyword, Boolean enabled);

    /**
     * 新建公告（CN-IN-06 / CN-35）。
     *
     * @param dto 公告信息（不得含 id）
     * @return 新建公告ID
     */
    Long createNotice(NoticeDTO dto);

    /**
     * 编辑/启停公告（CN-IN-07 / CN-36，CAS 于 id + update_time）。
     *
     * @param dto 公告信息（id 必填，其余可选）
     * @return 公告ID
     */
    Long updateNotice(NoticeDTO dto);
}
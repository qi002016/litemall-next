package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PageVO;
import com.litemall.domain.PO.Notice;
import com.litemall.domain.VO.NoticeVO;

public interface INoticeService extends IService<Notice> {

    /**
     * 分页获取通知公告列表
     */
    PageVO<NoticeVO> getNoticeList(int pageNum, int pageSize);
}

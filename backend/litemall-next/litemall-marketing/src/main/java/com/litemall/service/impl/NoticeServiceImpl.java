package com.litemall.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.PageVO;
import com.litemall.domain.PO.Notice;
import com.litemall.domain.VO.NoticeVO;
import com.litemall.mapper.NoticeMapper;
import com.litemall.service.INoticeService;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements INoticeService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PageVO<NoticeVO> getNoticeList(int pageNum, int pageSize) {
        Page<Notice> page = new Page<>(pageNum, pageSize);
        Page<Notice> p = lambdaQuery()
                .eq(Notice::getDeleted, false)
                .orderByDesc(Notice::getAddTime)
                .page(page);
        List<NoticeVO> voList = p.getRecords().stream().map(n -> {
            NoticeVO vo = new NoticeVO();
            vo.setId(n.getId().longValue());
            vo.setTitle(n.getTitle());
            vo.setContent(n.getContent());
            vo.setAddTime(n.getAddTime() == null ? null : n.getAddTime().format(DT));
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(p.getTotal(), p.getPages(), voList);
    }
}

package com.litemall.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.PageVO;
import com.litemall.domain.PO.Topic;
import com.litemall.domain.VO.TopicVO;
import com.litemall.mapper.TopicMapper;
import com.litemall.service.ITopicService;
import com.litemall.utils.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TopicServiceImpl extends ServiceImpl<TopicMapper, Topic> implements ITopicService {

    @Override
    public PageVO<TopicVO> getTopicList(int pageNum, int pageSize) {
        Page<Topic> page = new Page<>(pageNum, pageSize);
        Page<Topic> p = lambdaQuery()
                .eq(Topic::getDeleted, false)
                // R2：C 端只展示启用中的专题，管理端停用（enabled=false）后即刻不可见
                .eq(Topic::getEnabled, true)
                .orderByAsc(Topic::getSortOrder)
                .orderByDesc(Topic::getAddTime)
                .page(page);
        List<TopicVO> voList = BeanUtils.copyList(p.getRecords(), TopicVO.class);
        return new PageVO<>(p.getTotal(), p.getPages(), voList);
    }
}

package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PageVO;
import com.litemall.domain.PO.Topic;
import com.litemall.domain.VO.TopicVO;

public interface ITopicService extends IService<Topic> {

    /**
     * 分页获取专题列表
     */
    PageVO<TopicVO> getTopicList(int pageNum, int pageSize);
}

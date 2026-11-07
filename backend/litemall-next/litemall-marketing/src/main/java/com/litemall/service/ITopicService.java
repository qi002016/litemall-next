package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PO.Topic;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.TopicDetailVO;
import com.litemall.domain.VO.TopicVO;

public interface ITopicService extends IService<Topic> {

    /**
     * 分页获取专题列表
     */
    PageVO<TopicVO> getTopicList(int pageNum, int pageSize);

    /**
     * C 端专题详情（R3-02 D7）：不存在/已删除 → 404；goods 串里失效的商品 id 跳过，不 500。
     */
    TopicDetailVO getTopicDetail(Long topicId);
}

package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.PO.SearchHistory;

import java.util.List;

/**
 * <p>
 * 搜索历史表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
public interface ISearchHistoryService extends IService<SearchHistory> {

    /**
     * 获取当前登录用户的搜索历史关键词
     *
     * @return 关键词数组
     */
    List<String> getSearchHistory();

    /**
     * 清空当前登录用户的搜索历史
     */
    void clearSearchHistory();

    /**
     * 记录一次搜索（已存在则置顶更新时间）
     */
    void recordSearch(String keyword);
}

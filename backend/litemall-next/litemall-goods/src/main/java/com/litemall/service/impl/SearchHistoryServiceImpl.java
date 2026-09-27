package com.litemall.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.PO.SearchHistory;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.SearchHistoryMapper;
import com.litemall.service.ISearchHistoryService;
import com.litemall.utils.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 搜索历史表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Service
public class SearchHistoryServiceImpl extends ServiceImpl<SearchHistoryMapper, SearchHistory> implements ISearchHistoryService {

    @Override
    public List<String> getSearchHistory() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }
        return lambdaQuery()
                .eq(SearchHistory::getUserId, userId)
                .eq(SearchHistory::getDeleted, false)
                .orderByDesc(SearchHistory::getAddTime)
                .list()
                .stream()
                .map(SearchHistory::getKeyword)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public void clearSearchHistory() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }
        // 物理删除当前用户的搜索历史
        lambdaUpdate()
                .eq(SearchHistory::getUserId, userId)
                .remove();
    }

    @Override
    public void recordSearch(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        Long userId = UserContext.getUser();
        if (userId == null) {
            // 未登录不记录，静默忽略
            return;
        }
        // 已存在该关键词则先物理删除，再重新插入，使其排到最前
        lambdaUpdate()
                .eq(SearchHistory::getUserId, userId)
                .eq(SearchHistory::getKeyword, keyword)
                .remove();
        SearchHistory history = new SearchHistory()
                .setUserId(userId.intValue())
                .setKeyword(keyword)
                .setFrom("h5")
                .setAddTime(LocalDateTime.now())
                .setUpdateTime(LocalDateTime.now())
                .setDeleted(false);
        save(history);
    }
}

package com.litemall.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.UserClient;
import com.litemall.domain.DTO.FeedbackSubmitDTO;
import com.litemall.domain.PO.Feedback;
import com.litemall.domain.VO.FeedbackVO;
import com.litemall.domain.dto.UserInfoDTO;
import com.litemall.mapper.FeedbackMapper;
import com.litemall.service.IFeedbackService;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 意见反馈服务 实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, Feedback> implements IFeedbackService {

    private final UserClient userClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeedbackVO submit(FeedbackSubmitDTO dto) {
        Long userId = UserContext.getUser();
        String username = resolveUsername(userId);
        List<String> pictures = dto.getPictures();
        LocalDateTime now = LocalDateTime.now();
        Feedback feedback = new Feedback()
                .setUserId(userId == null ? 0L : userId)
                .setUsername(username)
                .setMobile(dto.getMobile() == null ? "" : dto.getMobile())
                .setFeedType("feedback")
                .setContent(dto.getContent())
                .setStatus(0)
                .setHasPicture(pictures != null && !pictures.isEmpty())
                .setPicUrls(pictures == null || pictures.isEmpty() ? null : JSONUtil.toJsonStr(pictures))
                .setAddTime(now)
                .setUpdateTime(now)
                .setDeleted(false);
        save(feedback);
        FeedbackVO vo = new FeedbackVO();
        vo.setFeedbackId(feedback.getId());
        return vo;
    }

    /**
     * 通过 user 服务获取用户昵称作为反馈用户名；获取不到时回退为 user{id}
     */
    private String resolveUsername(Long userId) {
        if (userId == null) {
            return "";
        }
        try {
            List<UserInfoDTO> users = userClient.listUserByIds(List.of(userId));
            if (users != null && !users.isEmpty() && users.get(0).getNickname() != null) {
                return users.get(0).getNickname();
            }
        } catch (Exception ignored) {
            // user 服务不可用时回退
        }
        return "user" + userId;
    }
}

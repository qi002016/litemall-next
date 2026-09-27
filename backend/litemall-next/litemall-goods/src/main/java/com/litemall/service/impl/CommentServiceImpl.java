package com.litemall.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.UserClient;
import com.litemall.domain.DTO.CommentDTO;
import com.litemall.domain.PO.Comment;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CommentVO;
import com.litemall.domain.dto.UserInfoDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.CommentMapper;
import com.litemall.service.ICommentService;
import com.litemall.utils.BeanUtils;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 评论表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements ICommentService {

    private final UserClient userClient;

    @Override
    public PageVO<CommentVO> getCommentList(Long goodsId, int pageNum, int pageSize) {
        if (goodsId == null) {
            throw new BadRequestException("商品ID不能为空");
        }
        Page<Comment> page = new Page<>(pageNum, pageSize);
        // 1.分页查询商品评论(type=0 商品评论, value_id=商品ID)
        Page<Comment> p = lambdaQuery()
                .eq(Comment::getValueId, goodsId)
                .eq(Comment::getType, 0)
                .eq(Comment::getDeleted, false)
                .orderByDesc(Comment::getAddTime)
                .page(page);
        List<Comment> comments = p.getRecords();
        if (comments == null || comments.isEmpty()) {
            return PageVO.empty(p);
        }
        // 2.通过Feign客户端跨服务批量查询评论用户信息(昵称/头像)
        Set<Long> userIds = comments.stream()
                .map(c -> c.getUserId().longValue())
                .collect(Collectors.toSet());
        List<UserInfoDTO> userInfos = userClient.listUserByIds(List.copyOf(userIds));
        Map<Long, UserInfoDTO> userMap = userInfos == null ? Map.of()
                : userInfos.stream()
                .collect(Collectors.toMap(UserInfoDTO::getId, Function.identity()));
        // 3.组装VO
        List<CommentVO> voList = comments.stream().map(c -> {
            CommentVO vo = BeanUtils.copyBean(c, CommentVO.class);
            // picUrls JSON数组字符串 -> String[]
            if (StringUtils.hasText(c.getPicUrls())) {
                vo.setPicUrls(JSONUtil.toList(c.getPicUrls(), String.class).toArray(new String[0]));
            }
            UserInfoDTO user = userMap.get(c.getUserId().longValue());
            if (user != null) {
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
            }
            return vo;
        }).collect(Collectors.toList());
        return PageVO.of(p, voList);
    }

    @Override
    public void addComment(CommentDTO dto) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }
        Comment comment = new Comment();
        comment.setValueId(dto.getGoodsId().intValue());
        comment.setType(0);
        comment.setContent(dto.getContent());
        comment.setStar(dto.getStar());
        comment.setUserId(userId.intValue());
        List<String> picUrls = dto.getPicUrls();
        boolean hasPicture = picUrls != null && !picUrls.isEmpty();
        comment.setHasPicture(hasPicture);
        if (hasPicture) {
            comment.setPicUrls(JSONUtil.toJsonStr(picUrls));
        }
        comment.setDeleted(false);
        save(comment);
    }
}

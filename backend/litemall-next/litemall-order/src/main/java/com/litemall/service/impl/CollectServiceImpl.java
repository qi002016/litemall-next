package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.client.GoodsClient;
import com.litemall.domain.DTO.CollectAddDTO;
import com.litemall.domain.PO.Collect;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.CollectVO;
import com.litemall.domain.dto.GoodsBriefDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.CollectMapper;
import com.litemall.service.ICollectService;
import com.litemall.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 * 收藏表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Service
@RequiredArgsConstructor
public class CollectServiceImpl extends ServiceImpl<CollectMapper, Collect> implements ICollectService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 收藏类型：0 商品 */
    private static final int TYPE_GOODS = 0;

    private final GoodsClient goodsClient;

    @Override
    public PageVO<CollectVO> listCollect(Integer pageNum, Integer pageSize) {
        Long userId = UserContext.getUser();
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Page<Collect> page = new Page<>(pn, ps);
        Page<Collect> collectPage = lambdaQuery()
                .eq(Collect::getUserId, userId)
                .eq(Collect::getType, TYPE_GOODS)
                .eq(Collect::getDeleted, false)
                .orderByDesc(Collect::getAddTime)
                .page(page);

        List<Collect> records = collectPage.getRecords();
        if (records.isEmpty()) {
            return new PageVO<>(collectPage.getTotal(), collectPage.getPages(), List.of());
        }

        // 批量查商品信息（Feign）
        List<Long> goodsIds = records.stream()
                .map(c -> c.getValueId())
                .collect(Collectors.toList());
        List<GoodsBriefDTO> briefList = goodsClient.listGoodsBriefByIds(goodsIds);
        Map<Long, GoodsBriefDTO> briefMap = briefList.stream()
                .collect(Collectors.toMap(GoodsBriefDTO::getId, Function.identity(), (a, b) -> a));

        List<CollectVO> voList = records.stream().map(c -> {
            CollectVO vo = new CollectVO();
            vo.setId(c.getId());
            vo.setGoodsId(c.getValueId());
            GoodsBriefDTO brief = briefMap.get(c.getValueId());
            if (brief != null) {
                vo.setGoodsName(brief.getName());
                vo.setPicUrl(brief.getPicUrl());
                vo.setPrice(brief.getPrice());
                vo.setOnSale(Boolean.TRUE.equals(brief.getOnSale()));
            } else {
                // 商品被物理删或查不到：goodsName/picUrl/price 保持 null，不给空串/0；
                // onSale 固定 false，让前端按「已下架/不可用」渲染而不是当作可购买
                vo.setOnSale(false);
            }
            vo.setAddTime(c.getAddTime() == null ? null : c.getAddTime().format(DT));
            return vo;
        }).collect(Collectors.toList());
        return new PageVO<>(collectPage.getTotal(), collectPage.getPages(), voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addCollect(CollectAddDTO dto) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }
        if (dto.getGoodsId() == null) {
            throw new BadRequestException("商品ID不能为空", BadRequestException.PARAM_INVALID);
        }

        // 参数校验前置：引用的商品必须存在且未删除，避免写出指向空商品的收藏记录；
        // 抛 PARAM_INVALID 后由全局 advice 统一转「对外恒 200 + data.errorCode」的响应
        List<GoodsBriefDTO> briefList = goodsClient.listGoodsBriefByIds(List.of(dto.getGoodsId()));
        if (briefList == null || briefList.isEmpty()) {
            throw new BadRequestException("商品不存在或已删除", BadRequestException.PARAM_INVALID);
        }

        if (dto.getType() == 0) {
            // 收藏：先查存在，命中即幂等返回 true，不重复插入（BR-FAV-02）
            Collect exist = lambdaQuery()
                    .eq(Collect::getUserId, userId)
                    .eq(Collect::getValueId, dto.getGoodsId())
                    .eq(Collect::getType, TYPE_GOODS)
                    .eq(Collect::getDeleted, false)
                    .one();
            if (exist != null) {
                return true;
            }
            LocalDateTime now = LocalDateTime.now();
            Collect collect = new Collect()
                    .setUserId(userId)
                    .setValueId(dto.getGoodsId())
                    .setType(TYPE_GOODS)
                    .setAddTime(now)
                    .setUpdateTime(now)
                    .setDeleted(false);
            try {
                save(collect);
            } catch (DuplicateKeyException e) {
                // 并发双击：唯一键（DDL 未上线时也靠这一层兜底）挡住第二条，
                // 收敛为「已收藏」而不是冒泡成 500；本事务内无后续写，吞掉后提交空事务无副作用
                return true;
            }
            return true;
        } else {
            // 取消：物理删除，不再用 deleted=true。逻辑删除会与 (user_id,value_id,type)
            // 唯一键打架——取消后重新收藏会撞键，且收藏偏好无需留痕
            lambdaUpdate()
                    .eq(Collect::getUserId, userId)
                    .eq(Collect::getValueId, dto.getGoodsId())
                    .eq(Collect::getType, TYPE_GOODS)
                    .remove();
            return false;
        }
    }
}

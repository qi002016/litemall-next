package com.litemall.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.cache.CacheService;
import com.litemall.domain.PO.Ad;
import com.litemall.domain.VO.AdVO;
import com.litemall.mapper.AdMapper;
import com.litemall.service.IAdService;
import com.litemall.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 广告表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-23
 */
@Service
@RequiredArgsConstructor
public class AdServiceImpl extends ServiceImpl<AdMapper, Ad> implements IAdService {

    private final CacheService cacheService;

    @Override
    public List<AdVO> getAdList(String position) {
        String key = "ad:list:" + position;
        return cacheService.getList(key, AdVO.class, 300, 60, () -> {
            List<Ad> adList = lambdaQuery().eq(Ad::getPosition, position).
                    eq(Ad::getEnabled, true).
                    eq(Ad::getDeleted, false).
                    lt(Ad::getStartTime, LocalDateTime.now()).
                    gt(Ad::getEndTime, LocalDateTime.now()).
                    list();
            return BeanUtils.copyList(adList, AdVO.class);
        });
    }

}

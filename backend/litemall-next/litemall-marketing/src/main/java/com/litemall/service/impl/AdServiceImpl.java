package com.litemall.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.cache.CacheService;
import com.litemall.domain.PO.Ad;
import com.litemall.domain.PageVO;
import com.litemall.domain.VO.AdVO;
import com.litemall.domain.dto.AdDTO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.AdMapper;
import com.litemall.service.IAdService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

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

    /** C 端只认这个位置名；库里 position 是 tinyint，1=首页轮播。映射放服务层，不改列类型（D1） */
    private static final String POSITION_HOME_BANNER = "home_banner";
    private static final int POSITION_HOME_BANNER_VALUE = 1;

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 5;

    /** 广告列表缓存 key 前缀；三个写入口统一按这个前缀失效（D6） */
    private static final String AD_LIST_CACHE_PREFIX = "ad:list:";

    private final CacheService cacheService;

    @Override
    public PageVO<AdVO> getAdList(String position, Integer limit) {
        // 未知 position（含前端漏传的 null）直接给空分页：不查库也不碰缓存，
        // 避免把字符串 eq 到 tinyint 列上恒 0 行，更避免无意义地缓存一个空结果
        if (!POSITION_HOME_BANNER.equals(position)) {
            return PageVO.empty(0L, 0L);
        }
        int size = normalizeLimit(limit);
        String key = AD_LIST_CACHE_PREFIX + position;
        List<AdVO> cached = cacheService.getList(key, AdVO.class, 300, 60, () -> {
            LocalDateTime now = LocalDateTime.now();
            // 把 wrapper 直接交给 baseMapper.selectList，而不是 ServiceImpl.lambdaQuery().list()：
            // 两者生成的 SQL 完全一致，但链式 wrapper 会要求 mapper 是 MyBatis 代理
            //（取 mapperInterface/sqlSession），纯 Mockito 离线单测拿不到这些属性会直接报错。
            List<Ad> rows = baseMapper.selectList(
                    Wrappers.lambdaQuery(Ad.class)
                            // home_banner → 1，Integer 对 Integer，不再把 String 传给 tinyint 列（D1）
                            .eq(Ad::getPosition, POSITION_HOME_BANNER_VALUE)
                            .eq(Ad::getEnabled, true)
                            .eq(Ad::getDeleted, false)
                            // 空即不限：start/end 为 NULL 的行不能被整行排除（D3）
                            .and(w -> w.isNull(Ad::getStartTime).or().le(Ad::getStartTime, now))
                            .and(w -> w.isNull(Ad::getEndTime).or().ge(Ad::getEndTime, now))
                            // 排序值越小越前，同值按 id 倒序（D5）
                            .orderByAsc(Ad::getSortOrder)
                            .orderByDesc(Ad::getId)
                            .last("LIMIT " + MAX_LIMIT));
            return rows.stream().map(this::toAdVO).toList();
        });
        // 截断放在缓存之外：不同 limit 共用一个 key，若在 loader 内按 limit 截断会互相污染（BR-CMS-03）
        List<AdVO> list = cached.size() > size ? cached.subList(0, size) : cached;
        long total = list.size();
        return new PageVO<>(total, total == 0 ? 0L : 1L, list);
    }

    @Override
    public PageVO<AdDTO> adminListAds(Integer pageNum, Integer pageSize, String name) {
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Page<Ad> p = lambdaQuery()
                .eq(Ad::getDeleted, false)
                .like(StringUtils.hasText(name), Ad::getName, name)
                // 后台列表与 C 端同序，运营看到的顺序就是首页顺序（D5）
                .orderByAsc(Ad::getSortOrder)
                .orderByDesc(Ad::getId)
                .page(new Page<>(page, size));
        return PageVO.of(p, this::toAdDTO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAd(AdDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw new BadRequestException("广告名称不能为空");
        }
        // 新建缺省为"无跳转"，避免旧调用方不带 linkType 时把脏 URL 带进库
        int linkType = dto.getLinkType() == null ? 0 : dto.getLinkType();
        assertLinkType(linkType);
        String link = normalizeLink(linkType, dto.getLink());
        Integer sortOrder = dto.getSortOrder() == null ? 0 : dto.getSortOrder();

        LocalDateTime now = LocalDateTime.now();
        Ad ad = new Ad();
        ad.setName(dto.getName());
        ad.setLink(link);
        ad.setUrl(dto.getUrl());
        ad.setPosition(dto.getPosition());
        ad.setContent(dto.getContent());
        ad.setStartTime(dto.getStartTime());
        ad.setEndTime(dto.getEndTime());
        ad.setEnabled(dto.getEnabled());
        // D4：linkType/sortOrder 必须落库，改造前这两个字段在写入口被整段丢弃
        ad.setLinkType(linkType);
        ad.setSortOrder(sortOrder);
        ad.setDeleted(Boolean.FALSE);
        ad.setAddTime(now);
        ad.setUpdateTime(now);
        save(ad);
        invalidateAdListCache();
        return ad.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAd(AdDTO dto) {
        Ad exists = requireAd(dto);
        Integer linkType = dto.getLinkType();
        if (linkType != null) {
            assertLinkType(linkType);
        }
        // linkType 显式给出才按规则处理 link；未给出表示本次不调整跳转，保持原有部分更新语义
        String link = linkType == null ? dto.getLink() : normalizeLink(linkType, dto.getLink());

        Ad ad = new Ad();
        ad.setId(exists.getId());
        ad.setName(dto.getName());
        ad.setLink(link);
        ad.setUrl(dto.getUrl());
        ad.setPosition(dto.getPosition());
        ad.setContent(dto.getContent());
        ad.setStartTime(dto.getStartTime());
        ad.setEndTime(dto.getEndTime());
        ad.setEnabled(dto.getEnabled());
        ad.setLinkType(linkType);
        ad.setSortOrder(dto.getSortOrder());
        ad.setUpdateTime(LocalDateTime.now());
        updateById(ad);

        // updateById 的 NOT_NULL 策略会跳过 link=null；linkType=0 时显式把脏 URL 清成 NULL，
        // 否则"无跳转"的广告仍留着 /pages/xxx 这类小程序路径（D4/D8）
        if (linkType != null && linkType == 0) {
            update(new LambdaUpdateWrapper<Ad>()
                    .eq(Ad::getId, exists.getId())
                    .set(Ad::getLink, null));
        }
        invalidateAdListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAd(Long id) {
        if (id == null) {
            throw new BadRequestException("广告ID不能为空");
        }
        Ad ad = getById(id);
        if (ad == null || Boolean.TRUE.equals(ad.getDeleted())) {
            throw new BadRequestException("广告不存在");
        }
        Ad update = new Ad();
        update.setId(id);
        update.setDeleted(Boolean.TRUE);
        update.setUpdateTime(LocalDateTime.now());
        updateById(update);
        invalidateAdListCache();
    }

    /**
     * 缓存失效必须在事务提交后：三个写入口都在 @Transactional 内，提交前 del 会让并发读
     * 用未提交的旧快照回源，把旧值重新写回缓存（TTL 300s），停用广告后首页最长 300s 不变（R3-04）。
     * 事务同步激活时注册 afterCommit；无事务上下文（如单测/直调）时立即删。
     */
    private void invalidateAdListCache() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.delByPrefix(AD_LIST_CACHE_PREFIX);
                }
            });
            return;
        }
        cacheService.delByPrefix(AD_LIST_CACHE_PREFIX);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /** link 里只允许存商品/专题 ID 的十进制串，不存 URL（BR-CMS-02、§7.3 第 8 条） */
    private void assertLinkType(int linkType) {
        if (linkType != 0 && linkType != 1 && linkType != 2) {
            throw new BadRequestException("跳转类型只能是 0/1/2", BadRequestException.PARAM_INVALID);
        }
    }

    private String normalizeLink(int linkType, String rawLink) {
        if (linkType == 0) {
            // 无跳转：把 link 置空，不留脏值
            return null;
        }
        String link = rawLink == null ? null : rawLink.trim();
        if (!StringUtils.hasText(link) || !link.matches("\\d+")) {
            throw new BadRequestException("跳转目标ID必须是非空数字", BadRequestException.PARAM_INVALID);
        }
        return link;
    }

    private Ad requireAd(AdDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BadRequestException("广告ID不能为空");
        }
        Ad ad = getById(dto.getId());
        if (ad == null || Boolean.TRUE.equals(ad.getDeleted())) {
            throw new BadRequestException("广告不存在");
        }
        return ad;
    }

    private AdVO toAdVO(Ad ad) {
        AdVO vo = new AdVO();
        vo.setId(ad.getId());
        vo.setName(ad.getName());
        vo.setUrl(ad.getUrl());
        vo.setLink(ad.getLink());
        vo.setContent(ad.getContent());
        // 出参返位置名而非库里的 tinyint，对齐 §7.4 示例与前端约定
        vo.setPosition(POSITION_HOME_BANNER);
        vo.setLinkType(ad.getLinkType());
        vo.setSortOrder(ad.getSortOrder());
        return vo;
    }

    private AdDTO toAdDTO(Ad ad) {
        AdDTO dto = new AdDTO();
        dto.setId(ad.getId());
        dto.setName(ad.getName());
        dto.setLink(ad.getLink());
        dto.setUrl(ad.getUrl());
        dto.setPosition(ad.getPosition());
        dto.setContent(ad.getContent());
        dto.setStartTime(ad.getStartTime());
        dto.setEndTime(ad.getEndTime());
        dto.setEnabled(ad.getEnabled());
        dto.setLinkType(ad.getLinkType());
        dto.setSortOrder(ad.getSortOrder());
        dto.setAddTime(ad.getAddTime());
        dto.setUpdateTime(ad.getUpdateTime());
        dto.setDeleted(ad.getDeleted());
        return dto;
    }
}

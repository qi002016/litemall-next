package com.litemall.service.impl;

import cn.hutool.json.JSONUtil;
import com.litemall.domain.PO.Goods;
import com.litemall.domain.PO.GoodsProduct;
import com.litemall.domain.PO.GoodsSpecification;
import com.litemall.domain.VO.GoodsDetailVO;
import com.litemall.exception.BadRequestException;
import com.litemall.mapper.GoodsProductMapper;
import com.litemall.service.IGoodsProductService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.service.IGoodsService;
import com.litemall.service.IGoodsSpecificationService;
import com.litemall.utils.BeanUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 * 商品货品表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-22
 */
@Service
public class GoodsProductServiceImpl extends ServiceImpl<GoodsProductMapper, GoodsProduct> implements IGoodsProductService {


}

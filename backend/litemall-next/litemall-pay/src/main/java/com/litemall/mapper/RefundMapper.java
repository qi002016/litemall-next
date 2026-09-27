package com.litemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litemall.domain.PO.Refund;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 退款单表 Mapper
 * </p>
 *
 * @author qihuang
 * @since 2026-09-25
 */
@Mapper
public interface RefundMapper extends BaseMapper<Refund> {
}

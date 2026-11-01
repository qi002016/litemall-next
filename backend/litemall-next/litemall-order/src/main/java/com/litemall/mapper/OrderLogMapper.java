package com.litemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litemall.domain.PO.OrderLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper（表 {@code litemall_log}）。
 * <p>order 侧只写不删：CN-10/CN-11 留痕、CN-12 互斥计数、CN-33 派生 {@code shipUpdateCount}。</p>
 */
@Mapper
public interface OrderLogMapper extends BaseMapper<OrderLog> {
}
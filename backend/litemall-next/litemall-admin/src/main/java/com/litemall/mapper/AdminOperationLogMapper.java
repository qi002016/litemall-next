package com.litemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litemall.domain.AdminOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理端操作日志 Mapper（admin 域自有表 litemall_log）。
 */
@Mapper
public interface AdminOperationLogMapper extends BaseMapper<AdminOperationLog> {
}
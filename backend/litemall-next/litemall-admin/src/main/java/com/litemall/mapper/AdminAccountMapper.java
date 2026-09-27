package com.litemall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.litemall.domain.AdminAccount;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理端账号 Mapper（admin 域自有表 litemall_admin）。
 */
@Mapper
public interface AdminAccountMapper extends BaseMapper<AdminAccount> {
}
package com.litemall.mapper;

import com.litemall.domain.PO.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 用户表 Mapper 接口
 * </p>
 *
 * @author qihuang
 * @since 2026-09-20
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

}

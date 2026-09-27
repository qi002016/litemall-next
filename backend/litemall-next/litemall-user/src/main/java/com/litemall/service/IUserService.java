package com.litemall.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.litemall.domain.DTO.CaptchaDTO;
import com.litemall.domain.DTO.PasswordUpdateDTO;
import com.litemall.domain.DTO.UserLoginFormDTO;
import com.litemall.domain.DTO.UserRegisterDTO;
import com.litemall.domain.DTO.UserUpdateInfoDTO;
import com.litemall.domain.DTO.WxLoginDTO;
import com.litemall.domain.PO.User;
import com.litemall.domain.VO.AddressVO;
import com.litemall.domain.VO.UserInfoVO;
import com.litemall.domain.VO.UserLoginVO;
import com.litemall.domain.dto.UserInfoDTO;

import java.util.Collection;
import java.util.List;


/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-20
 */
public interface IUserService extends IService<User> {

    /**
     * 登录
     * @param userLoginFormDTO
     * @return
     */
    UserLoginVO login(UserLoginFormDTO userLoginFormDTO);

    /**
     * 注册
     * @param userLoginFormDTO
     * @return
     */
    UserLoginVO register(UserRegisterDTO userLoginFormDTO);

    String getCaptcha(CaptchaDTO userRegisterDTO);

    /**
     * 微信小程序登录(不存在则自动注册)
     */
    UserLoginVO wxLogin(WxLoginDTO dto);

    /**
     * 获取用户信息
     * @param userId
     * @return
     */
    UserInfoVO getUserInfo(Long userId);

    /**
     * 更新用户信息
     *
     * @param nickname
     * @param avatar
     * @param gender
     * @return
     */
    UserInfoVO updateUserInfo(UserUpdateInfoDTO dto);

    /**
     * 更新用户密码
     *
     * @param dto oldPassword/newPassword
     */
    void updatePassword(PasswordUpdateDTO dto);

    /**
     * 根据用户ID批量查询用户信息（内部接口, 供Feign调用）
     *
     * @param ids 用户ID集合
     * @return 用户信息列表
     */
    List<UserInfoDTO> listUserByIds(Collection<Long> ids);

}

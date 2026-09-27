package com.litemall.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.litemall.domain.DTO.CaptchaDTO;
import com.litemall.domain.DTO.PasswordUpdateDTO;
import com.litemall.domain.DTO.UserLoginFormDTO;
import com.litemall.domain.DTO.UserRegisterDTO;
import com.litemall.domain.DTO.UserUpdateInfoDTO;
import com.litemall.domain.DTO.WxLoginDTO;
import com.litemall.domain.PO.Address;
import com.litemall.domain.PO.User;
import com.litemall.domain.TokenPrincipal;
import com.litemall.domain.VO.AddressVO;
import com.litemall.domain.VO.UserInfoVO;
import com.litemall.domain.VO.UserLoginVO;
import com.litemall.domain.PageVO;
import com.litemall.domain.dto.UserAdminDTO;
import com.litemall.domain.dto.UserInfoDTO;
import com.litemall.domain.dto.UserStatusDTO;
import com.litemall.enums.UserStatus;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.ForbiddenException;
import com.litemall.jwt.JwtProperties;
import com.litemall.jwt.JwtTool;
import com.litemall.mapper.UserMapper;
import com.litemall.notify.NotifyService;
import com.litemall.notify.NotifyType;
import com.litemall.service.IUserService;
import com.litemall.utils.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author qihuang
 * @since 2026-09-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final PasswordEncoder passwordEncoder;
    private final JwtTool jwtTool;
    private final JwtProperties jwtProperties;
    private final CaptchaCodeUtil captchaCodeUtil;
    private final NotifyService notifyService;
    private final WechatMiniService wechatMiniService;

    @Override
    public UserLoginVO login(UserLoginFormDTO userLoginDTO) {
        // 1.按用户名查询
        String username = userLoginDTO.getUsername();
        String password = userLoginDTO.getPassword();
        User user = lambdaQuery().eq(User::getUsername, username).one();
        if (user == null) {
            throw new BadRequestException("用户名或密码错误");
        }
        // 2.校验账号状态
        if (UserStatus.FROZEN.equals(user.getStatus())) {
            throw new ForbiddenException("用户被冻结");
        }
        if (UserStatus.CANCEL.equals(user.getStatus())) {
            throw new ForbiddenException("用户已注销");
        }
        // 3.校验密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException("用户名或密码错误");
        }
        return buildLoginVO(user);
    }

    @Override
    public UserLoginVO register(UserRegisterDTO dto) {
        // 1.先校验验证码(快速失败,防止刷接口)
        boolean valid = captchaCodeUtil.validate(dto.getMobile(), dto.getCode());
        if (!valid) {
            throw new BadRequestException("验证码错误或已过期");
        }

        // 2.校验用户名/手机号是否已注册
        long usernameCount = lambdaQuery().eq(User::getUsername, dto.getUsername()).count();
        if (usernameCount > 0) {
            throw new BadRequestException("用户名已存在");
        }
        long mobileCount = lambdaQuery().eq(User::getMobile, dto.getMobile()).count();
        if (mobileCount > 0) {
            throw new BadRequestException("该手机号已注册");
        }

        // 3.创建用户
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setMobile(dto.getMobile());
        user.setStatus(UserStatus.NORMAL.getValue());
        save(user);
        return buildLoginVO(user);
    }

    @Override
    public String getCaptcha(CaptchaDTO captchaDTO) {
        String phone = captchaDTO.getPhone();
        // 1.生成 6 位数字验证码
        String code = captchaCodeUtil.generate();
        log.info("phone: {}, code: {}", phone, code);
        // 2.写入 Redis(60 秒内重发会被拒绝)
        boolean saved = captchaCodeUtil.save(phone, code);
        if (!saved) {
            throw new BadRequestException("验证码发送过于频繁,请 60 秒后再试");
        }
        // 3.调用短信渠道发送(本地 NoopSmsSender 只打日志)
        notifyService.notifySmsTemplate(phone, NotifyType.CAPTCHA, new String[]{code});
        return "发送成功";
    }

    @Override
    public UserLoginVO wxLogin(WxLoginDTO dto) {
        // 1.code 换 openid + session_key
        WxSession session = wechatMiniService.code2Session(dto.getCode());
        String openid = session.openid();
        if (openid == null || openid.isBlank()) {
            throw new BadRequestException("微信登录失败,code 无效");
        }

        // 2.按 openid 查用户
        User user = lambdaQuery().eq(User::getWeixinOpenid, openid).one();
        if (user == null) {
            // 3.不存在则自动注册
            user = new User();
            user.setWeixinOpenid(openid);
            user.setUsername(openid);
            user.setNickname(dto.getNickname() != null ? dto.getNickname() : "微信用户");
            user.setAvatar(dto.getAvatarUrl());
            user.setGender(dto.getGender() != null ? dto.getGender() : 0);
            user.setUserLevel(0);
            user.setStatus(UserStatus.NORMAL.getValue());
            user.setLastLoginTime(LocalDateTime.now());
            save(user);
        } else {
            // 4.已存在则更新最后登录时间
            if (UserStatus.FROZEN.equals(user.getStatus())) {
                throw new ForbiddenException("用户被冻结");
            }
            if (UserStatus.CANCEL.equals(user.getStatus())) {
                throw new ForbiddenException("用户已注销");
            }
            user.setLastLoginTime(LocalDateTime.now());
            updateById(user);
        }
        return buildLoginVO(user);
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        UserInfoVO vo = BeanUtils.copyBean(user, UserInfoVO.class);
        return  vo;
    }

    @Override
    public UserInfoVO updateUserInfo(UserUpdateInfoDTO dto) {
        Long userId = UserContext.getUser();
        boolean update = lambdaUpdate().eq(User::getId, userId)
                .set(dto.getNickname() != null, User::getNickname, dto.getNickname())
                .set(dto.getAvatar() != null, User::getAvatar, dto.getAvatar())
                .set(dto.getGender() != null, User::getGender, dto.getGender())
                .update();
        if (!update) {
            throw new BadRequestException("更新用户信息失败");
        }
        User user = getById(userId);
        UserInfoVO vo = BeanUtils.copyBean(user, UserInfoVO.class);
        return vo;
    }

    @Override
    public void updatePassword(PasswordUpdateDTO dto) {
        User user = getById(UserContext.getUser());
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        updateById(user);
    }

    @Override
    public List<UserInfoDTO> listUserByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return lambdaQuery()
                .in(User::getId, ids)
                .list()
                .stream()
                .map(u -> {
                    UserInfoDTO dto = new UserInfoDTO();
                    dto.setId(u.getId().longValue());
                    dto.setNickname(u.getNickname());
                    dto.setAvatar(u.getAvatar());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private UserLoginVO buildLoginVO(User user) {
        Long userId = user.getId().longValue();
        String token = jwtTool.createToken(userId, TokenPrincipal.ROLE_USER, TokenPrincipal.AUD_APP,
                jwtProperties.getTokenTTL());
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(userId);
        vo.setUsername(user.getUsername());
        vo.setToken(token);
        return vo;
    }

    /* ==================== H5：管理端用户（admin 服务 Feign 调用） ==================== */

    @Override
    public PageVO<UserAdminDTO> adminListUsers(Integer pageNum, Integer pageSize, String username) {
        int pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int ps = pageSize == null || pageSize < 1 || pageSize > 100 ? 10 : pageSize;
        Page<User> p = lambdaQuery()
                .eq(User::getDeleted, false)
                .like(StringUtils.hasText(username), User::getUsername, username)
                .orderByDesc(User::getId)
                .page(new Page<>(pn, ps));
        // 字段与改造前 admin 端返回的 AdminUser 保持一致：id/username/nickname/mobile/userLevel/status/addTime
        return PageVO.of(p, u -> {
            UserAdminDTO dto = new UserAdminDTO();
            dto.setId(u.getId());
            dto.setUsername(u.getUsername());
            dto.setNickname(u.getNickname());
            dto.setMobile(u.getMobile());
            dto.setUserLevel(u.getUserLevel());
            dto.setStatus(u.getStatus());
            dto.setAddTime(u.getAddTime());
            return dto;
        });
    }

    @Override
    public long adminCountUsers() {
        return lambdaQuery().eq(User::getDeleted, false).count();
    }

    @Override
    public void adminUpdateUserStatus(UserStatusDTO dto) {
        if (dto == null || dto.getId() == null || dto.getStatus() == null) {
            throw new BadRequestException("用户ID与状态不能为空");
        }
        // 取值收口在 user 域自己的枚举上（0 可用 / 1 禁用 / 2 注销），非法值直接拒绝
        UserStatus target = UserStatus.of(dto.getStatus());
        User user = getById(dto.getId());
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw new BadRequestException("用户不存在");
        }
        if (target.getValue() == (user.getStatus() == null ? -1 : user.getStatus())) {
            // 状态未变化：幂等直接返回，避免无意义写库
            return;
        }
        user.setStatus(target.getValue());
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);
    }
}

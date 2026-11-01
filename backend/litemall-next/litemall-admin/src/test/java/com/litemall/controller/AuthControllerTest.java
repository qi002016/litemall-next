package com.litemall.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.litemall.domain.AdminAccount;
import com.litemall.domain.DTO.AdminLoginDTO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.AdminLoginVO;
import com.litemall.mapper.AdminAccountMapper;
import com.litemall.support.AdminLoginLogger;
import com.litemall.support.AdminSessionVersion;
import com.litemall.support.AdminTokenIssuer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CI-06 登录（05-R2 实施口径 §5.14 / P-1 / P-2 / D-36）。
 * <p>
 * 覆盖三类必须区分的失败：密码错（401 LOGIN_FAILED，含用户名不存在）、账号禁用
 * （403 ADMIN_DISABLED，且不签发令牌）、成功（令牌携带当前 {@code auth:ver} 版本）。
 * 同时断言失败/成功都写 {@code type=2} 登录日志——登录对外恒 HTTP 200，
 * 拦截器判不出成败，只能由本入口负责。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AdminAccountMapper adminAccountMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AdminTokenIssuer tokenIssuer;
    @Mock
    private AdminSessionVersion sessionVersion;
    @Mock
    private AdminLoginLogger loginLogger;

    @InjectMocks
    private AuthController controller;

    private static AdminLoginDTO loginBody() {
        AdminLoginDTO body = new AdminLoginDTO();
        body.setUsername("boss");
        body.setPassword("secret");
        return body;
    }

    private static AdminAccount account(int status) {
        AdminAccount a = new AdminAccount();
        a.setId(7L);
        a.setUsername("boss");
        a.setPassword("enc");
        a.setStatus(status);
        a.setAvatar("a.png");
        a.setDeleted(false);
        return a;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/auth/login");
        request.setRemoteAddr("10.0.0.9");
        return request;
    }

    @Test
    @DisplayName("密码错 → 401 LOGIN_FAILED（文案不区分账号是否存在）")
    void wrongPassword() {
        when(adminAccountMapper.selectOne(any(Wrapper.class))).thenReturn(account(1));
        when(passwordEncoder.matches("secret", "enc")).thenReturn(false);

        Result<AdminLoginVO> result = controller.login(loginBody(), request());

        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMsg()).isEqualTo("用户名或密码错误");
        // 失败响应 data 是 Map（{"errorCode":...}），不能用 Result<AdminLoginVO> 的泛型 getter 断言
        assertThat((Object) result.getData()).isEqualTo(Map.of("errorCode", "LOGIN_FAILED"));
        verify(loginLogger).record("boss", "10.0.0.9", 7L, false, "失败(LOGIN_FAILED)");
        verifyNoInteractions(tokenIssuer, sessionVersion);
    }

    @Test
    @DisplayName("账号不存在 → 同样 401 LOGIN_FAILED（防枚举），日志 targetId=0")
    void unknownUsername() {
        when(adminAccountMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        Result<AdminLoginVO> result = controller.login(loginBody(), request());

        assertThat(result.getCode()).isEqualTo(401);
        assertThat(result.getMsg()).isEqualTo("用户名或密码错误");
        verify(loginLogger).record("boss", "10.0.0.9", null, false, "失败(LOGIN_FAILED)");
        verifyNoInteractions(tokenIssuer);
    }

    @Test
    @DisplayName("账号被禁用 → 403 ADMIN_DISABLED，不签发令牌")
    void disabledAccount() {
        when(adminAccountMapper.selectOne(any(Wrapper.class))).thenReturn(account(0));
        when(passwordEncoder.matches("secret", "enc")).thenReturn(true);

        Result<AdminLoginVO> result = controller.login(loginBody(), request());

        assertThat(result.getCode()).isEqualTo(403);
        assertThat(result.getMsg()).isEqualTo("账号已被禁用");
        assertThat((Object) result.getData()).isEqualTo(Map.of("errorCode", "ADMIN_DISABLED"));
        verify(loginLogger).record("boss", "10.0.0.9", 7L, false, "失败(ADMIN_DISABLED)");
        verifyNoInteractions(tokenIssuer, sessionVersion);
    }

    @Test
    @DisplayName("登录成功 → 令牌写入当前 auth:ver 版本，并记录成功日志")
    void loginSuccessCarriesVersion() {
        when(adminAccountMapper.selectOne(any(Wrapper.class))).thenReturn(account(1));
        when(passwordEncoder.matches("secret", "enc")).thenReturn(true);
        when(sessionVersion.current(7L)).thenReturn(3);
        when(tokenIssuer.issue(7L, 3)).thenReturn("TOKEN");

        Result<AdminLoginVO> result = controller.login(loginBody(), request());

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getToken()).isEqualTo("TOKEN");
        assertThat(result.getData().getId()).isEqualTo(7L);
        assertThat(result.getData().getUsername()).isEqualTo("boss");
        assertThat(result.getData().getAvatar()).isEqualTo("a.png");
        verify(loginLogger).record("boss", "10.0.0.9", 7L, true, "成功");
    }

    @Test
    @DisplayName("Redis 不可用（版本未知）→ 按 ver=0 降级签发，登录不被缓存故障阻断")
    void loginDegradesWhenRedisUnavailable() {
        when(adminAccountMapper.selectOne(any(Wrapper.class))).thenReturn(account(1));
        when(passwordEncoder.matches("secret", "enc")).thenReturn(true);
        when(sessionVersion.current(7L)).thenReturn(null);
        when(tokenIssuer.issue(7L, 0)).thenReturn("TOKEN");

        Result<AdminLoginVO> result = controller.login(loginBody(), request());

        assertThat(result.getCode()).isEqualTo(200);
        verify(tokenIssuer).issue(7L, 0);
    }

    @Test
    @DisplayName("登录失败响应的细分码落在 data.errorCode（03 §4.1 结构）")
    void errorCodeLandsInData() {
        when(adminAccountMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        Result<AdminLoginVO> result = controller.login(loginBody(), request());

        assertThat(result.getCode()).isEqualTo(401);
        assertThat((Object) result.getData()).isEqualTo(Map.of("errorCode", "LOGIN_FAILED"));
    }
}
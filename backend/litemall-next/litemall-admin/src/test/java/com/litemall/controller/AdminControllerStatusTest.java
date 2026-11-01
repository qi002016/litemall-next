package com.litemall.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.litemall.domain.AdminAccount;
import com.litemall.domain.DTO.AdminStatusDTO;
import com.litemall.domain.DTO.AdminCreateDTO;
import com.litemall.domain.Result;
import com.litemall.domain.VO.AdminStatusVO;
import com.litemall.exception.BadRequestException;
import com.litemall.exception.CommonException;
import com.litemall.mapper.AdminAccountMapper;
import com.litemall.support.AdminSessionVersion;
import com.litemall.utils.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * CN-37（{@code POST /admin/account/status}）与 CI-04 / CI-02 的行为验证。
 * <p>
 * 校验顺序是口径卡 §3.15 的硬约束（400 → 403 自己 → 404 不存在 → 200 幂等 →
 * 409 最后一个启用账号 → CAS → INCR），因此每个用例都断言「前面命中时后面不再执行」，
 * 否则顺序错了也不会被发现。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerStatusTest {

    private static final Long ME = 1L;

    /**
     * LambdaUpdateWrapper/LambdaQueryWrapper 依赖 MyBatis-Plus 的 TableInfo 缓存；
     * 纯 Mockito 单测没有 Spring 容器，必须显式初始化，否则报
     * “can not find lambda cache for this entity [AdminAccount]”。
     */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AdminAccount.class);
    }

    @Mock
    private AdminAccountMapper adminAccountMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AdminSessionVersion sessionVersion;

    @InjectMocks
    private AdminController controller;

    @BeforeEach
    void setUp() {
        UserContext.setUser(ME);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    // ---------- CN-37 ----------

    @Test
    @DisplayName("status=5 → 400 PARAM_INVALID，且不碰数据库")
    void invalidStatusRejected() {
        AdminStatusDTO body = new AdminStatusDTO();
        body.setId(2L);
        body.setStatus(5);

        assertThatThrownBy(() -> controller.status(body))
                .isInstanceOfSatisfying(BadRequestException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(400);
                    assertThat(e.getErrorCode()).isEqualTo(BadRequestException.PARAM_INVALID);
                });
        verifyNoInteractions(adminAccountMapper);
    }

    @Test
    @DisplayName("id 为空 → 400 PARAM_INVALID")
    void missingIdRejected() {
        AdminStatusDTO body = new AdminStatusDTO();
        body.setStatus(0);

        assertThatThrownBy(() -> controller.status(body))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(BadRequestException.PARAM_INVALID));
    }

    @Test
    @DisplayName("禁用自己 → 403 ADMIN_SELF_FORBIDDEN，且不读库")
    void cannotDisableSelf() {
        AdminStatusDTO body = new AdminStatusDTO();
        body.setId(ME);
        body.setStatus(0);

        assertThatThrownBy(() -> controller.status(body))
                .isInstanceOfSatisfying(CommonException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(403);
                    assertThat(e.getErrorCode()).isEqualTo("ADMIN_SELF_FORBIDDEN");
                    assertThat(e.getMessage()).isEqualTo("不能禁用自己");
                });
        verifyNoInteractions(adminAccountMapper);
    }

    @Test
    @DisplayName("目标账号不存在/已删除 → 404 RESOURCE_NOT_FOUND")
    void targetNotFound() {
        AdminStatusDTO body = new AdminStatusDTO();
        body.setId(9L);
        body.setStatus(0);
        when(adminAccountMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> controller.status(body))
                .isInstanceOfSatisfying(CommonException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(404);
                    assertThat(e.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
                });
        verify(adminAccountMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("已是目标状态 → 200 幂等，不更新、不 INCR（重复失效会话无意义）")
    void idempotentWhenAlreadyTarget() {
        AdminStatusDTO body = new AdminStatusDTO();
        body.setId(2L);
        body.setStatus(0);
        when(adminAccountMapper.selectById(2L)).thenReturn(account(2L, AdminStatusVO.DISABLED));

        Result<AdminStatusVO> result = controller.status(body);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().status()).isEqualTo(0);
        assertThat(result.getData().statusText()).isEqualTo("禁用");
        verify(adminAccountMapper, never()).update(isNull(), any());
        verifyNoInteractions(sessionVersion);
    }

    @Test
    @DisplayName("禁用最后一个启用账号 → 409 ADMIN_LAST_ENABLED（D-35 默认实现）")
    void cannotDisableLastEnabled() {
        AdminStatusDTO body = new AdminStatusDTO();
        body.setId(2L);
        body.setStatus(0);
        when(adminAccountMapper.selectById(2L)).thenReturn(account(2L, AdminStatusVO.ENABLED));
        when(adminAccountMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> controller.status(body))
                .isInstanceOfSatisfying(CommonException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(409);
                    assertThat(e.getErrorCode()).isEqualTo("ADMIN_LAST_ENABLED");
                });
        verify(adminAccountMapper, never()).update(isNull(), any());
        verifyNoInteractions(sessionVersion);
    }

    @Test
    @DisplayName("禁用成功 → CAS 更新 + INCR auth:ver + statusText=禁用")
    void disableSucceeds() {
        AdminStatusDTO body = new AdminStatusDTO();
        body.setId(2L);
        body.setStatus(0);
        when(adminAccountMapper.selectById(2L)).thenReturn(account(2L, AdminStatusVO.ENABLED));
        when(adminAccountMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        when(adminAccountMapper.update(isNull(), any())).thenReturn(1);

        Result<AdminStatusVO> result = controller.status(body);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().id()).isEqualTo(2L);
        assertThat(result.getData().status()).isEqualTo(0);
        assertThat(result.getData().statusText()).isEqualTo("禁用");
        verify(sessionVersion).increase(2L);
    }

    @Test
    @DisplayName("启用成功 → 不 INCR（启用不需要踢旧会话）")
    void enableDoesNotIncreaseVersion() {
        AdminStatusDTO body = new AdminStatusDTO();
        body.setId(2L);
        body.setStatus(1);
        when(adminAccountMapper.selectById(2L)).thenReturn(account(2L, AdminStatusVO.DISABLED));
        when(adminAccountMapper.update(isNull(), any())).thenReturn(1);

        Result<AdminStatusVO> result = controller.status(body);

        assertThat(result.getData().statusText()).isEqualTo("启用");
        verifyNoInteractions(sessionVersion);
    }

    @Test
    @DisplayName("CAS 影响 0 行但回读已是目标态（并发重复提交）→ 200 幂等")
    void casZeroButAlreadyTarget() {
        AdminStatusDTO body = new AdminStatusDTO();
        body.setId(2L);
        body.setStatus(0);
        when(adminAccountMapper.selectById(2L))
                .thenReturn(account(2L, AdminStatusVO.ENABLED))
                .thenReturn(account(2L, AdminStatusVO.DISABLED));
        when(adminAccountMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        when(adminAccountMapper.update(isNull(), any())).thenReturn(0);

        Result<AdminStatusVO> result = controller.status(body);

        assertThat(result.getCode()).isEqualTo(200);
        verify(sessionVersion, never()).increase(anyLong());
    }

    @Test
    @DisplayName("CAS 影响 0 行且回读仍是旧态（极端并发）→ 500 操作失败，请重试")
    void casZeroAndStillOld() {
        AdminStatusDTO body = new AdminStatusDTO();
        body.setId(2L);
        body.setStatus(0);
        when(adminAccountMapper.selectById(2L)).thenReturn(account(2L, AdminStatusVO.ENABLED));
        when(adminAccountMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        when(adminAccountMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> controller.status(body))
                .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getCode()).isEqualTo(500));
    }

    @Test
    @DisplayName("INCR 失败 → 500 操作失败，请重试（不得静默成功）")
    void redisFailureMustNotBeSilent() {
        AdminStatusDTO body = new AdminStatusDTO();
        body.setId(2L);
        body.setStatus(0);
        when(adminAccountMapper.selectById(2L)).thenReturn(account(2L, AdminStatusVO.ENABLED));
        when(adminAccountMapper.selectCount(any(Wrapper.class))).thenReturn(3L);
        when(adminAccountMapper.update(isNull(), any())).thenReturn(1);
        when(sessionVersion.increase(2L)).thenThrow(new CommonException("操作失败，请重试", 500));

        assertThatThrownBy(() -> controller.status(body))
                .isInstanceOfSatisfying(CommonException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(500);
                    assertThat(e.getMessage()).isEqualTo("操作失败，请重试");
                });
    }

    // ---------- CI-04 重置口令 ----------

    @Test
    @DisplayName("重置口令：先 404 判定，再 INCR，最后改库")
    void resetPasswordNotFound() {
        var body = new com.litemall.domain.DTO.AdminResetPwdDTO();
        body.setId(9L);
        body.setPassword("newpass");
        when(adminAccountMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> controller.resetPwd(body))
                .isInstanceOfSatisfying(CommonException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND"));
        verifyNoInteractions(sessionVersion, passwordEncoder);
    }

    @Test
    @DisplayName("重置口令成功：INCR 在改库之前（Redis 失败则口令不变）")
    void resetPasswordIncreasesVersionBeforeUpdate() {
        var body = new com.litemall.domain.DTO.AdminResetPwdDTO();
        body.setId(2L);
        body.setPassword("newpass");
        when(adminAccountMapper.selectById(2L)).thenReturn(account(2L, AdminStatusVO.ENABLED));
        when(passwordEncoder.encode("newpass")).thenReturn("enc");

        Result<Void> result = controller.resetPwd(body);

        assertThat(result.getCode()).isEqualTo(200);
        var order = inOrder(sessionVersion, adminAccountMapper, passwordEncoder);
        order.verify(sessionVersion).increase(2L);
        order.verify(passwordEncoder).encode("newpass");
        order.verify(adminAccountMapper).updateById(any(AdminAccount.class));
    }

    // ---------- CI-02 账号名唯一 ----------

    @Test
    @DisplayName("创建账号：用户名已存在 → 409 ADMIN_USERNAME_DUPLICATED")
    void createDuplicateUsername() {
        AdminCreateDTO body = new AdminCreateDTO();
        body.setUsername("dup");
        body.setPassword("pwd");
        when(adminAccountMapper.selectOne(any(Wrapper.class))).thenReturn(account(3L, AdminStatusVO.ENABLED));

        assertThatThrownBy(() -> controller.create(body))
                .isInstanceOfSatisfying(CommonException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(409);
                    assertThat(e.getErrorCode()).isEqualTo("ADMIN_USERNAME_DUPLICATED");
                });
        verify(adminAccountMapper, never()).insert(any(AdminAccount.class));
    }

    @Test
    @DisplayName("创建账号：唯一键竞争（先查漏、插入撞唯一索引）→ 仍 409 而非 500")
    void createDuplicateByIndex() {
        AdminCreateDTO body = new AdminCreateDTO();
        body.setUsername("dup");
        body.setPassword("pwd");
        when(adminAccountMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(passwordEncoder.encode("pwd")).thenReturn("enc");
        when(adminAccountMapper.insert(any(AdminAccount.class))).thenThrow(new DuplicateKeyException("uk_admin_username"));

        assertThatThrownBy(() -> controller.create(body))
                .isInstanceOfSatisfying(CommonException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("ADMIN_USERNAME_DUPLICATED"));
    }

    // ---------- CI-01 出参 statusText ----------

    @Test
    @DisplayName("账号列表：出参带 status/statusText，且不回传口令")
    void listCarriesStatusText() {
        Page<AdminAccount> page = new Page<>(1, 10);
        page.setRecords(List.of(account(1L, AdminStatusVO.ENABLED), account(2L, AdminStatusVO.DISABLED)));
        when(adminAccountMapper.selectPage(any(), any())).thenReturn(page);

        Result<com.litemall.domain.PageVO<AdminAccount>> result = controller.list(1, 10, null);

        assertThat(result.getData().getList()).extracting(AdminAccount::getStatusText)
                .containsExactly("启用", "禁用");
        assertThat(result.getData().getList()).allSatisfy(a -> assertThat(a.getPassword()).isNull());
    }

    // ---------- CI-03 自助改密（id == 当前账号 + password 非空） ----------

    @Test
    @DisplayName("CI-03 自助改密：旧密码正确 → 先 INCR auth:ver 再改库，落库为加密口令")
    void selfChangePasswordIncrementsBeforeUpdate() {
        AdminAccount body = new AdminAccount();
        body.setId(ME);
        body.setPassword("newpass1");
        body.setOldPassword("oldpass1");
        when(adminAccountMapper.selectById(ME)).thenReturn(account(ME, AdminStatusVO.ENABLED));
        when(passwordEncoder.matches("oldpass1", "enc")).thenReturn(true);
        when(passwordEncoder.encode("newpass1")).thenReturn("enc-new");
        when(adminAccountMapper.updateById(any(AdminAccount.class))).thenReturn(1);

        Result<Void> result = controller.update(body);

        assertThat(result.getCode()).isEqualTo(200);
        InOrder order = inOrder(sessionVersion, adminAccountMapper);
        order.verify(sessionVersion).increase(ME);
        order.verify(adminAccountMapper).updateById(any(AdminAccount.class));
        ArgumentCaptor<AdminAccount> captor = ArgumentCaptor.forClass(AdminAccount.class);
        verify(adminAccountMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("enc-new");
        assertThat(captor.getValue().getOldPassword()).isNull();
    }

    @Test
    @DisplayName("CI-03 自助改密：缺旧密码 → 400，不 INCR、不改库")
    void selfChangePasswordWithoutOldPassword() {
        AdminAccount body = new AdminAccount();
        body.setId(ME);
        body.setPassword("newpass1");
        when(adminAccountMapper.selectById(ME)).thenReturn(account(ME, AdminStatusVO.ENABLED));

        assertThatThrownBy(() -> controller.update(body))
                .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getCode()).isEqualTo(400));
        verifyNoInteractions(sessionVersion);
        verify(adminAccountMapper, never()).updateById(any(AdminAccount.class));
    }

    @Test
    @DisplayName("CI-03 自助改密：旧密码错误 → 400 PARAM_INVALID，不 INCR、不改库")
    void selfChangePasswordWrongOldPassword() {
        AdminAccount body = new AdminAccount();
        body.setId(ME);
        body.setPassword("newpass1");
        body.setOldPassword("bad");
        when(adminAccountMapper.selectById(ME)).thenReturn(account(ME, AdminStatusVO.ENABLED));
        when(passwordEncoder.matches("bad", "enc")).thenReturn(false);

        assertThatThrownBy(() -> controller.update(body))
                .isInstanceOfSatisfying(CommonException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(400);
                    assertThat(e.getErrorCode()).isEqualTo("PARAM_INVALID");
                });
        verifyNoInteractions(sessionVersion);
        verify(adminAccountMapper, never()).updateById(any(AdminAccount.class));
    }

    @Test
    @DisplayName("CI-03 自助改密：新口令长度不合法 → 400，且不查库")
    void selfChangePasswordTooShort() {
        AdminAccount body = new AdminAccount();
        body.setId(ME);
        body.setPassword("123");

        assertThatThrownBy(() -> controller.update(body))
                .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getCode()).isEqualTo(400));
        verify(adminAccountMapper, never()).selectById(any());
        verify(adminAccountMapper, never()).updateById(any(AdminAccount.class));
    }

    @Test
    @DisplayName("CI-03 自助改密：INCR 失败 → 500 且库内口令不变（先失效再改库）")
    void selfChangePasswordRedisFailureKeepsPassword() {
        AdminAccount body = new AdminAccount();
        body.setId(ME);
        body.setPassword("newpass1");
        body.setOldPassword("oldpass1");
        when(adminAccountMapper.selectById(ME)).thenReturn(account(ME, AdminStatusVO.ENABLED));
        when(passwordEncoder.matches("oldpass1", "enc")).thenReturn(true);
        when(sessionVersion.increase(ME)).thenThrow(new CommonException("操作失败，请重试", 500));

        assertThatThrownBy(() -> controller.update(body))
                .isInstanceOfSatisfying(CommonException.class, e -> assertThat(e.getCode()).isEqualTo(500));
        verify(adminAccountMapper, never()).updateById(any(AdminAccount.class));
    }

    @Test
    @DisplayName("CI-03 改他人账号：password 一律忽略（改密走 CI-04），不 INCR")
    void updateOtherAccountIgnoresPassword() {
        AdminAccount body = new AdminAccount();
        body.setId(2L);
        body.setPassword("newpass1");
        body.setOldPassword("whatever");
        when(adminAccountMapper.updateById(any(AdminAccount.class))).thenReturn(1);

        Result<Void> result = controller.update(body);

        assertThat(result.getCode()).isEqualTo(200);
        ArgumentCaptor<AdminAccount> captor = ArgumentCaptor.forClass(AdminAccount.class);
        verify(adminAccountMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPassword()).isNull();
        assertThat(captor.getValue().getOldPassword()).isNull();
        verifyNoInteractions(sessionVersion);
    }

    @Test
    @DisplayName("CI-03 编辑自己但不填 password：走普通更新，不 INCR")
    void selfUpdateWithoutPasswordDoesNotChangeVersion() {
        AdminAccount body = new AdminAccount();
        body.setId(ME);
        body.setAvatar("a.png");
        when(adminAccountMapper.updateById(any(AdminAccount.class))).thenReturn(1);

        Result<Void> result = controller.update(body);

        assertThat(result.getCode()).isEqualTo(200);
        verifyNoInteractions(sessionVersion);
    }

    private static AdminAccount account(Long id, int status) {
        AdminAccount a = new AdminAccount();
        a.setId(id);
        a.setUsername("u" + id);
        a.setPassword("enc");
        a.setStatus(status);
        a.setDeleted(false);
        return a;
    }
}
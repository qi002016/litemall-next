package com.litemall.jwt;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.litemall.domain.TokenPrincipal;
import com.litemall.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M11：统一后的 JWT 实现单元测试。
 * <p>
 * 重点覆盖此前三份复制导致的漂移：admin 版**缺少过期校验**，
 * 以及 H1 的"无 role/aud 的旧格式令牌必须被拒"。
 */
class JwtToolTest {

    private KeyPair keyPair;
    private JwtTool jwtTool;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        this.keyPair = generator.generateKeyPair();
        this.jwtTool = new JwtTool(keyPair);
    }

    @Test
    @DisplayName("M11：签发后可解析出 userId/role/aud")
    void createThenParse_roundTrip() {
        String token = jwtTool.createToken(42L, TokenPrincipal.ROLE_ADMIN, TokenPrincipal.AUD_ADMIN,
                Duration.ofMinutes(30));

        TokenPrincipal principal = jwtTool.parseToken(token);

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.role()).isEqualTo(TokenPrincipal.ROLE_ADMIN);
        assertThat(principal.aud()).isEqualTo(TokenPrincipal.AUD_ADMIN);
        assertThat(principal.hasRole(TokenPrincipal.ROLE_ADMIN)).isTrue();
        assertThat(principal.hasRole(TokenPrincipal.ROLE_USER)).isFalse();
        assertThat(principal.hasAud(TokenPrincipal.AUD_ADMIN)).isTrue();
    }

    @Test
    @DisplayName("M11：过期令牌必须被拒（admin 版本此前缺失该校验）")
    void expiredToken_rejected() {
        String token = jwtTool.createToken(1L, TokenPrincipal.ROLE_ADMIN, TokenPrincipal.AUD_ADMIN,
                Duration.ofMinutes(-1));

        assertThatThrownBy(() -> jwtTool.parseToken(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("过期");
    }

    @Test
    @DisplayName("M11：空令牌必须被拒")
    void nullToken_rejected() {
        assertThatThrownBy(() -> jwtTool.parseToken(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("M11：被篡改的令牌必须被拒")
    void tamperedToken_rejected() {
        String token = jwtTool.createToken(1L, TokenPrincipal.ROLE_USER, TokenPrincipal.AUD_APP,
                Duration.ofMinutes(30));

        assertThatThrownBy(() -> jwtTool.parseToken(token + "x"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("M11/H1：缺少 role/aud 的旧格式令牌必须被拒")
    void legacyTokenWithoutRoleAndAud_rejected() {
        JWTSigner signer = JWTSignerUtil.createSigner("rs256", keyPair);
        String legacy = JWT.create()
                .setPayload(TokenPrincipal.CLAIM_USER, 7L)
                .setExpiresAt(new Date(System.currentTimeMillis() + Duration.ofMinutes(10).toMillis()))
                .setSigner(signer)
                .sign();

        assertThatThrownBy(() -> jwtTool.parseToken(legacy))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("M11：用另一把私钥签发的令牌必须被拒")
    void tokenFromAnotherKey_rejected() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        JwtTool other = new JwtTool(generator.generateKeyPair());
        String token = other.createToken(9L, TokenPrincipal.ROLE_ADMIN, TokenPrincipal.AUD_ADMIN,
                Duration.ofMinutes(30));

        assertThatThrownBy(() -> jwtTool.parseToken(token))
                .isInstanceOf(UnauthorizedException.class);
    }
}
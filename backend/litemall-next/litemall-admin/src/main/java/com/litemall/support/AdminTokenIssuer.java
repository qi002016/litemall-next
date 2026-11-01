package com.litemall.support;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.litemall.domain.TokenPrincipal;
import com.litemall.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.time.Duration;
import java.util.Date;

/**
 * <p>
 * 管理端 access-token 签发（P-1）：在 {@link TokenPrincipal} 的四个标准声明
 * （{@code user}/{@code role}/{@code aud}/{@code exp}）之外，额外携带一个 <b>{@code ver}</b>
 * 会话版本声明（设计 06 §2「JWT 内携带 ver 声明」）。
 * </p>
 * <p>
 * 为什么在 admin 侧而不是扩展 {@code litemall-common-jwt}：{@code JwtTool} 是 gateway/user/admin
 * 三端共用的稳定件，R2 只允许 admin 域内落地 P-1（t12 任务范围），因此这里复用同一个
 * {@code KeyPair} 签名器签发同构令牌——C 端令牌格式完全不变，gateway 不受影响，
 * 校验仍由 {@code JwtTool#parseToken} 完成（签名/过期/三声明完整性）。
 * </p>
 *
 * @since 2026-11-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminTokenIssuer {

    /** 会话版本声明名（与 Redis key {@code auth:ver:{adminId}} 对应） */
    public static final String CLAIM_VER = "ver";

    private final KeyPair jwtKeyPair;
    private final JwtProperties jwtProperties;

    private volatile JWTSigner signer;

    /**
     * 签发管理端令牌。
     *
     * @param adminId 管理员 ID
     * @param version 当前会话版本（{@code auth:ver:{adminId}}，缺失时为 0）
     * @return 已签名的 access-token
     */
    public String issue(Long adminId, int version) {
        Duration ttl = jwtProperties.getTokenTTL();
        return JWT.create()
                .setPayload(TokenPrincipal.CLAIM_USER, adminId)
                .setPayload(TokenPrincipal.CLAIM_ROLE, TokenPrincipal.ROLE_ADMIN)
                .setPayload(TokenPrincipal.CLAIM_AUD, TokenPrincipal.AUD_ADMIN)
                .setPayload(CLAIM_VER, version)
                .setExpiresAt(new Date(System.currentTimeMillis() + ttl.toMillis()))
                .setSigner(signer())
                .sign();
    }

    /**
     * 读取令牌中的会话版本（只读载荷，不重复验签——调用前必须已由
     * {@code JwtTool#parseToken} 完成签名与过期校验）。
     *
     * @param token 已验签的令牌
     * @return 版本号；老令牌（无 {@code ver} 声明）或载荷异常时返回 {@code null}（调用方按 0 处理）
     */
    public static Integer readVersion(String token) {
        if (token == null) {
            return null;
        }
        try {
            Object payload = JWT.of(token).getPayload(CLAIM_VER);
            return payload == null ? null : Integer.valueOf(payload.toString());
        } catch (RuntimeException e) {
            log.debug("令牌 ver 声明解析失败（按 0 处理）：{}", e.toString());
            return null;
        }
    }

    private JWTSigner signer() {
        JWTSigner local = signer;
        if (local == null) {
            synchronized (this) {
                local = signer;
                if (local == null) {
                    local = JWTSignerUtil.createSigner("rs256", jwtKeyPair);
                    signer = local;
                }
            }
        }
        return local;
    }
}
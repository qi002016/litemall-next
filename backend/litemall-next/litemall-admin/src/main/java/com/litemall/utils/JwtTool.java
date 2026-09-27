package com.litemall.utils;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;
import com.litemall.domain.TokenPrincipal;
import com.litemall.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtTool {
    private final cn.hutool.jwt.signers.JWTSigner jwtSigner;

    public JwtTool(KeyPair keyPair) {
        this.jwtSigner = cn.hutool.jwt.signers.JWTSignerUtil.createSigner("rs256", keyPair);
    }

    /**
     * 创建 access-token，payload user = 管理员ID（H1：携带 role/aud）
     */
    public String createToken(Long userId, String role, String aud, Duration ttl) {
        return JWT.create()
                .setPayload(TokenPrincipal.CLAIM_USER, userId)
                .setPayload(TokenPrincipal.CLAIM_ROLE, role)
                .setPayload(TokenPrincipal.CLAIM_AUD, aud)
                .setExpiresAt(new Date(System.currentTimeMillis() + ttl.toMillis()))
                .setSigner(jwtSigner)
                .sign();
    }

    /**
     * 解析 token（M11：补过期校验，admin 版此前缺失 → 令牌永不过期）
     */
    public TokenPrincipal parseToken(String token) {
        if (token == null) {
            throw new UnauthorizedException("未登录");
        }
        JWT jwt;
        try {
            jwt = JWT.of(token).setSigner(jwtSigner);
        } catch (Exception e) {
            throw new UnauthorizedException("无效的token", e);
        }
        if (!jwt.verify()) {
            throw new UnauthorizedException("无效的token");
        }
        try {
            JWTValidator.of(jwt).validateDate();
        } catch (ValidateException e) {
            throw new UnauthorizedException("token已经过期");
        }
        Object userPayload = jwt.getPayload(TokenPrincipal.CLAIM_USER);
        Object rolePayload = jwt.getPayload(TokenPrincipal.CLAIM_ROLE);
        Object audPayload = jwt.getPayload(TokenPrincipal.CLAIM_AUD);
        if (userPayload == null || rolePayload == null || audPayload == null) {
            throw new UnauthorizedException("无效的token");
        }
        return new TokenPrincipal(
                Long.valueOf(userPayload.toString()),
                rolePayload.toString(),
                audPayload.toString());
    }
}

package com.litemall.utils;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.litemall.domain.TokenPrincipal;
import com.litemall.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtTool {
    private final JWTSigner jwtSigner;

    public JwtTool(KeyPair keyPair) {
        this.jwtSigner = JWTSignerUtil.createSigner("rs256", keyPair);
    }

    /**
     * 创建 access-token（H1：载荷携带 role 与 aud）
     *
     * @param userId 用户ID
     * @param role   角色，见 {@link TokenPrincipal#ROLE_USER} / {@link TokenPrincipal#ROLE_ADMIN}
     * @param aud    受众，见 {@link TokenPrincipal#AUD_APP} / {@link TokenPrincipal#AUD_ADMIN}
     * @param ttl    有效期
     * @return access-token
     */
    public String createToken(Long userId, String role, String aud, Duration ttl) {
        // 1.生成jws
        return JWT.create()
                .setPayload(TokenPrincipal.CLAIM_USER, userId)
                .setPayload(TokenPrincipal.CLAIM_ROLE, role)
                .setPayload(TokenPrincipal.CLAIM_AUD, aud)
                .setExpiresAt(new Date(System.currentTimeMillis() + ttl.toMillis()))
                .setSigner(jwtSigner)
                .sign();
    }

    /**
     * 解析token
     *
     * @param token token
     * @return 令牌主体（userId + role + aud）
     */
    public TokenPrincipal parseToken(String token) {
        // 1.校验token是否为空
        if (token == null) {
            throw new UnauthorizedException("未登录");
        }
        // 2.校验并解析jwt
        JWT jwt;
        try {
            jwt = JWT.of(token).setSigner(jwtSigner);
        } catch (Exception e) {
            throw new UnauthorizedException("无效的token", e);
        }
        // 2.校验jwt是否有效
        if (!jwt.verify()) {
            // 验证失败
            throw new UnauthorizedException("无效的token");
        }
        // 3.校验是否过期
        try {
            JWTValidator.of(jwt).validateDate();
        } catch (ValidateException e) {
            throw new UnauthorizedException("token已经过期");
        }
        // 4.数据格式校验（H1：role/aud 必须存在）
        Object userPayload = jwt.getPayload(TokenPrincipal.CLAIM_USER);
        Object rolePayload = jwt.getPayload(TokenPrincipal.CLAIM_ROLE);
        Object audPayload = jwt.getPayload(TokenPrincipal.CLAIM_AUD);
        if (userPayload == null || rolePayload == null || audPayload == null) {
            // 数据为空或旧格式令牌
            throw new UnauthorizedException("无效的token");
        }

        // 5.数据解析
        try {
            return new TokenPrincipal(
                    Long.valueOf(userPayload.toString()),
                    rolePayload.toString(),
                    audPayload.toString());
        } catch (RuntimeException e) {
            // 数据格式有误
            throw new UnauthorizedException("无效的token");
        }
    }
}

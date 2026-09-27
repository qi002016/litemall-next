package com.litemall.jwt;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.litemall.domain.TokenPrincipal;
import com.litemall.exception.UnauthorizedException;

import java.security.KeyPair;
import java.time.Duration;
import java.util.Date;

/**
 * M11：JWT 签发与校验的唯一实现（RS256）。
 * <p>
 * 此前 gateway / user / admin 各有一份复制，且 admin 版缺少过期校验（令牌永不过期）；
 * 现统一到本类，三端行为完全一致：
 * <ul>
 *   <li>签名校验（RS256，密钥来自密钥库）</li>
 *   <li>有效期校验（{@link JWTValidator#validateDate()}）</li>
 *   <li>载荷完整性校验（H1：user / role / aud 三声明缺一不可）</li>
 * </ul>
 * 说明：原 user 版 {@code parseToken(String) -> Long} 在仓库内**零调用方**，
 * 统一后由 {@link #parseToken(String)} 返回 {@link TokenPrincipal} 覆盖其能力。
 */
public class JwtTool {

    private final JWTSigner jwtSigner;

    public JwtTool(KeyPair keyPair) {
        this.jwtSigner = JWTSignerUtil.createSigner("rs256", keyPair);
    }

    /**
     * 创建 access-token（H1：载荷携带 role 与 aud）
     *
     * @param userId 用户/管理员 ID
     * @param role   角色，见 {@link TokenPrincipal#ROLE_USER} / {@link TokenPrincipal#ROLE_ADMIN}
     * @param aud    受众，见 {@link TokenPrincipal#AUD_APP} / {@link TokenPrincipal#AUD_ADMIN}
     * @param ttl    有效期
     * @return 已签名的 access-token
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
     * 解析并校验 token
     *
     * @param token access-token
     * @return 令牌主体（userId + role + aud）
     * @throws UnauthorizedException 为空 / 签名无效 / 已过期 / 载荷不完整
     */
    public TokenPrincipal parseToken(String token) {
        // 1.校验 token 是否为空
        if (token == null) {
            throw new UnauthorizedException("未登录");
        }
        // 2.解析并校验签名
        JWT jwt;
        try {
            jwt = JWT.of(token).setSigner(jwtSigner);
        } catch (Exception e) {
            throw new UnauthorizedException("无效的token", e);
        }
        // NF-4：签名被篡改/长度非法时 hutool 的 verify() 会抛 CryptoException 而不是返回 false，
        // 若不在此处收口，会向上冒泡成 500（而非 401）。任何验签失败一律按"无效令牌"处理。
        boolean verified;
        try {
            verified = jwt.verify();
        } catch (Exception e) {
            throw new UnauthorizedException("无效的token", e);
        }
        if (!verified) {
            throw new UnauthorizedException("无效的token");
        }
        // 3.校验是否过期（M11：三端统一，admin 此前缺失）
        try {
            JWTValidator.of(jwt).validateDate();
        } catch (ValidateException e) {
            throw new UnauthorizedException("token已经过期");
        }
        // 4.载荷完整性（H1：role/aud 必须存在，旧格式令牌一律拒绝）
        Object userPayload = jwt.getPayload(TokenPrincipal.CLAIM_USER);
        Object rolePayload = jwt.getPayload(TokenPrincipal.CLAIM_ROLE);
        Object audPayload = jwt.getPayload(TokenPrincipal.CLAIM_AUD);
        if (userPayload == null || rolePayload == null || audPayload == null) {
            throw new UnauthorizedException("无效的token");
        }
        // 5.类型转换
        try {
            return new TokenPrincipal(
                    Long.valueOf(userPayload.toString()),
                    rolePayload.toString(),
                    audPayload.toString());
        } catch (RuntimeException e) {
            throw new UnauthorizedException("无效的token", e);
        }
    }
}
package com.litemall.domain;

/**
 * 令牌主体：从 access-token 解析出的身份信息。
 * <p>
 * H1：令牌载荷除用户 ID 外，还必须携带角色({@code role})与受众({@code aud})，
 * 供网关做路径级 RBAC，避免 C 端普通用户令牌调用管理端接口。
 */
public record TokenPrincipal(Long userId, String role, String aud) {

    /** 角色：C 端普通用户 */
    public static final String ROLE_USER = "user";
    /** 角色：管理端 */
    public static final String ROLE_ADMIN = "admin";

    /** 受众：C 端应用 */
    public static final String AUD_APP = "litemall-app";
    /** 受众：管理端 */
    public static final String AUD_ADMIN = "litemall-admin";

    /** 令牌载荷字段名 */
    public static final String CLAIM_USER = "user";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_AUD = "aud";

    public boolean hasRole(String expected) {
        return expected != null && expected.equals(role);
    }

    public boolean hasAud(String expected) {
        return expected != null && expected.equals(aud);
    }
}

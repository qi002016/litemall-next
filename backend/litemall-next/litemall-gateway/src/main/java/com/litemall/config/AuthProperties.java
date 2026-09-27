package com.litemall.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "hm.auth")
public class AuthProperties {
    private List<String> includePaths;
    private List<String> excludePaths;
    /** H1：路径前缀 → 要求的令牌角色/受众 */
    private List<RoleRule> roleRules;

    @Data
    public static class RoleRule {
        /** Ant 风格路径，如 /admin/** */
        private String pattern;
        /** 要求的角色，见 TokenPrincipal.ROLE_* */
        private String role;
        /** 要求的受众，见 TokenPrincipal.AUD_* */
        private String aud;
    }
}

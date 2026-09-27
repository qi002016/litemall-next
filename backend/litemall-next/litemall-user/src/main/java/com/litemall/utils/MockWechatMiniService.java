package com.litemall.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 微信小程序服务默认实现(占位)。
 * <p>
 * H8：仅在显式开启（{@code hm.wx.mock.enabled=true}，dev 配置中打开）时注册。
 * 生产环境不开启 → 本 bean 不存在 → {@code UserServiceImpl} 缺少 {@link WechatMiniService}
 * 依赖而启动失败，杜绝"任意 code 生成任意 openid"的账户接管风险。
 * 接入真实微信时，写一个 {@link WechatMiniService} 实现类并标注 @Primary 即可覆盖本实现。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "hm.wx.mock", name = "enabled", havingValue = "true")
public class MockWechatMiniService implements WechatMiniService {

    @Override
    public WxSession code2Session(String code) {
        log.warn("[MockWechatMiniService] 正在使用 mock 微信登录(hm.wx.mock.enabled=true)，严禁用于生产");
        // 占位:用 code 哈希生成一个固定 openid,便于本地调试
        String mockOpenid = "mock_openid_" + Integer.toHexString(code.hashCode());
        log.info("[MockWechatMiniService] code={} -> openid={}", code, mockOpenid);
        return new WxSession(mockOpenid, "mock_session_key");
    }
}

package com.litemall.utils;

/**
 * 微信小程序服务接口
 * <p>
 * 使用方式:
 * 1. 引入微信小程序 SDK:
 * <pre>
 * &lt;dependency&gt;
 *   &lt;groupId&gt;com.github.binarywang&lt;/groupId&gt;
 *   &lt;artifactId&gt;weixin-java-miniapp&lt;/artifactId&gt;
 *   &lt;version&gt;4.6.0&lt;/version&gt;
 * &lt;/dependency&gt;
 * </pre>
 * 2. 配置 appid/secret,替换 code2Session 的真实调用
 */
public interface WechatMiniService {

    /**
     * 用前端 wx.login() 返回的 code 换取 openid 和 session_key
     *
     * @param code 临时登录凭证
     * @return 微信会话
     */
    WxSession code2Session(String code);
}

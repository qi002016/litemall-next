package com.litemall.utils;

/**
 * 微信小程序登录会话
 *
 * @param openid      用户唯一标识
 * @param session_key 会话密钥(后续解密手机号等敏感数据用)
 */
public record WxSession(String openid, String session_key) {
}

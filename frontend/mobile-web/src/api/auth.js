import request from '@/utils/request'

// 账号登录 POST /app/auth/login
export const login = (data) => request({ url: '/app/auth/login', method: 'post', data })

// 注册 POST /app/auth/register
export const register = (data) => request({ url: '/app/auth/register', method: 'post', data })

// 发送短信验证码 POST /app/auth/captcha
export const sendCaptcha = (data) => request({ url: '/app/auth/captcha', method: 'post', data })

// 微信小程序登录 POST /app/auth/wx-login
export const wxLogin = (data) => request({ url: '/app/auth/wx-login', method: 'post', data })

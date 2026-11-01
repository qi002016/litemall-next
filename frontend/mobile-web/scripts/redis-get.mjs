/**
 * 极简 Redis RESP 客户端（仅支持 GET），供 E2E 脚本从本机 Redis 读取演示验证码。
 * 用途：后端 /app/auth/captcha 在演示环境只回「发送成功」，真实码以 captcha:{phone} 存在 Redis。
 */
import net from 'node:net'

export function redisGet(key, { host = '127.0.0.1', port = 6379, timeout = 2000 } = {}) {
  return new Promise((resolve) => {
    const sock = net.connect({ host, port })
    let buf = ''
    const done = (val) => {
      sock.destroy()
      resolve(val)
    }
    sock.setTimeout(timeout, () => done(null))
    sock.on('error', () => done(null))
    sock.on('connect', () => {
      sock.write(`*2\r\n$3\r\nGET\r\n$${Buffer.byteLength(key)}\r\n${key}\r\n`)
    })
    sock.on('data', (chunk) => {
      buf += chunk.toString('utf8')
      if (buf.startsWith('$-1')) return done(null)
      if (buf.startsWith('$')) {
        const idx = buf.indexOf('\r\n')
        const len = Number(buf.slice(1, idx))
        const body = buf.slice(idx + 2)
        if (Buffer.byteLength(body) >= len) return done(body.slice(0, len))
      }
      if (buf.startsWith('-')) return done(null)
    })
  })
}
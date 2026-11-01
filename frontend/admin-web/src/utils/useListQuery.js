import { reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * 列表页「查询状态 ↔ URL query」双向同步（R2-09：分页与筛选状态进 URL）。
 *
 * 不引入状态管理库：`route.query` 是唯一真源，页面拿到的是可自由改写的 reactive 对象，
 * 任何改动都会以 `router.replace` 写回 URL（不新增历史记录），刷新 / 前进后退 / 分享链接
 * 都能复原筛选与页码。
 *
 * 取值规则：
 * - 数字型默认值（`pageNum`/`pageSize`）→ Number，非法回退默认；
 * - 数组型默认值（多选筛选）→ URL 里用逗号拼接（`star=1,2,3`，与后端多值入参一致）；
 * - 其余 → 字符串；空串视为「不筛选」，不写进 URL。
 *
 * @param {object} defaults 查询默认值，同时决定字段类型
 * @param {object} [options]
 * @param {(raw: object, state: object) => object} [options.parse] 从 URL 还原时的额外钩子
 * @param {(query: object, state: object) => object} [options.serialize] 写 URL 前的额外钩子
 * @param {() => void} [options.onSync] 外部（前进/后退）回填状态后的回调，页面在这里重拉数据
 * @returns {{state: object, reset: () => void}} state 为 reactive；reset 恢复默认值
 */
export function useListQuery(defaults, options = {}) {
  const { parse, serialize, onSync } = options
  const route = useRoute()
  const router = useRouter()

  const fromUrl = () => {
    const raw = route.query || {}
    const next = { ...defaults }
    for (const key of Object.keys(defaults)) {
      const value = raw[key]
      if (value === undefined || value === null || value === '') continue
      const single = Array.isArray(value) ? value[0] : value
      const fallback = defaults[key]
      if (Array.isArray(fallback)) {
        next[key] = String(single)
          .split(',')
          .map((v) => v.trim())
          .filter(Boolean)
      } else if (typeof fallback === 'number') {
        const n = Number(single)
        if (Number.isFinite(n)) next[key] = n
      } else {
        next[key] = String(single)
      }
    }
    return parse ? { ...next, ...parse(raw, next) } : next
  }

  const state = reactive(fromUrl())

  const toUrl = () => {
    const query = {}
    for (const [key, value] of Object.entries(state)) {
      if (value === undefined || value === null || value === '') continue
      if (Array.isArray(value)) {
        if (value.length) query[key] = value.join(',')
        continue
      }
      query[key] = String(value)
    }
    return serialize ? serialize(query, state) : query
  }

  /** 只比较「规范成字符串」后的键值，避免 undefined/''/缺失互相误判成变更 */
  const sameQuery = (left, right) => {
    const l = left || {}
    const r = right || {}
    const keys = new Set([...Object.keys(l), ...Object.keys(r)])
    for (const key of keys) {
      const a = l[key] === undefined ? '' : String(l[key])
      const b = r[key] === undefined ? '' : String(r[key])
      if (a !== b) return false
    }
    return true
  }

  // 页面改筛选 → 写 URL（已一致则不写，避免自触发循环）
  watch(state, () => {
    const query = toUrl()
    if (sameQuery(route.query, query)) return
    router.replace({ path: route.path, query })
  })

  // 浏览器前进/后退 → 回填页面状态
  watch(
    () => route.query,
    () => {
      if (sameQuery(toUrl(), route.query)) return
      Object.assign(state, fromUrl())
      if (onSync) onSync()
    },
  )

  const reset = () => {
    Object.assign(state, cloneDefaults(defaults))
  }

  return { state, reset }
}

/** 深拷贝默认值（数组型默认值避免被页面改写后污染默认） */
function cloneDefaults(defaults) {
  return JSON.parse(JSON.stringify(defaults))
}
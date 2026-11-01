import { defineStore } from 'pinia'
import { cartList } from '@/api/cart'
import { msgList } from '@/api/msg'
import { useUserStore } from '@/store/user'

/** 全局轻量状态：购物车角标、未读消息角标（底部 Tab 用） */
export const useAppStore = defineStore('app', {
  state: () => ({
    cartCount: 0,
    unreadCount: 0,
  }),
  actions: {
    async refreshCartCount() {
      const user = useUserStore()
      if (!user.isLogin) {
        this.cartCount = 0
        return
      }
      try {
        const list = await cartList()
        const rows = Array.isArray(list) ? list : list?.list || []
        this.cartCount = rows
          .filter((it) => !it.invalidReason)
          .reduce((n, it) => n + (Number(it.number) || 0), 0)
      } catch {
        this.cartCount = 0
      }
    },
    async refreshUnread() {
      const user = useUserStore()
      if (!user.isLogin) {
        this.unreadCount = 0
        return
      }
      try {
        const page = await msgList({ pageNum: 1, pageSize: 50 })
        const rows = page?.list || (Array.isArray(page) ? page : [])
        this.unreadCount = rows.filter((m) => m.readStatus === false).length
      } catch {
        this.unreadCount = 0
      }
    },
    reset() {
      this.cartCount = 0
      this.unreadCount = 0
    },
  },
})
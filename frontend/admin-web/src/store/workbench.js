import { defineStore } from 'pinia'
import { dashboardStats } from '@/api/dashboard'

/**
 * 后台外壳的轻量协作状态：侧栏「售后处理」待办角标。
 * 取不到（后端未返回 pendingAftersaleCount / 接口未就绪）时保持 0，不打断页面。
 */
export const useWorkbenchStore = defineStore('workbench', {
  state: () => ({
    pendingAftersale: 0,
  }),
  actions: {
    async refreshPending() {
      try {
        const stats = (await dashboardStats({ period: '30d' })) || {}
        const n = Number(stats.pendingAftersaleCount)
        this.pendingAftersale = Number.isFinite(n) && n > 0 ? n : 0
      } catch {
        this.pendingAftersale = 0
      }
    },
  },
})
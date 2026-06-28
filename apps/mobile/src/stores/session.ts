import { defineStore } from 'pinia'
import { request, setToken } from '@/api/client'

export const useSessionStore = defineStore('session', {
  state: () => ({
    userId: 0,
    paired: false,
    couple: null as any,
    members: [] as any[]
  }),
  actions: {
    async mockLogin(phone: string, nickname: string) {
      const data = await request<{ token: string; userId: number }>('/auth/mock-login', {
        method: 'POST',
        data: { phone, nickname }
      })
      setToken(data.token)
      this.userId = data.userId
      await this.loadCouple()
    },
    async loadCouple() {
      const data = await request<any>('/couples/me')
      this.paired = data.paired
      this.couple = data.couple || null
      this.members = data.members || []
    }
  }
})

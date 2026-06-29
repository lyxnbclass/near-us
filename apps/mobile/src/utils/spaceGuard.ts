import { getToken, request } from '@/api/client'

export async function ensurePairedSpace() {
  if (!getToken()) {
    uni.navigateTo({ url: '/pages/auth/index' })
    return false
  }

  try {
    const relation = await request<any>('/couples/me')
    if (!relation?.paired) {
      uni.navigateTo({ url: '/pages/pair/index' })
      return false
    }
    return true
  } catch (error) {
    uni.navigateTo({ url: '/pages/auth/index' })
    return false
  }
}

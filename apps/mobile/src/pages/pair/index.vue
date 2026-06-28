<template>
  <view class="page romance-bg pair-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="intro">
        <text class="romance-kicker">A LITTLE CEREMONY</text>
        <text class="headline">点亮你们的空间</text>
        <text class="muted">一次配对，一处只属于两个人的私密角落。</text>
      </view>

      <view class="card glass-card block" :class="{ 'tap-glow': inviteGlow }">
        <text class="title">把这串密语交给 TA</text>
        <text class="muted">邀请码 24 小时有效，绑定后这里就只对你们两个人可见。</text>
        <view class="button" @click="createInvite">点亮我们的空间</view>
        <view v-if="inviteCode" class="secret-card pulse-soft">
          <text class="secret-label">OUR SECRET</text>
          <text class="code">{{ inviteCode }}</text>
        </view>
        <text v-if="inviteCode" class="muted">演示时可以把这个邀请码填到下面，完成配对。</text>
      </view>

      <view class="card glass-card block">
        <text class="title">我收到了密语</text>
        <input class="input" v-model="code" placeholder="输入对方的邀请码" />
        <view class="button" @click="bind">完成配对</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { request } from '@/api/client'

const inviteCode = ref('')
const code = ref('')
const inviteGlow = ref(false)

async function createInvite() {
  try {
    const data = await request<{ inviteCode: string }>('/couples/invite', { method: 'POST' })
    inviteCode.value = data.inviteCode
    code.value = data.inviteCode
    inviteGlow.value = true
    uni.showToast({ title: '邀请码已生成', icon: 'none' })
    setTimeout(() => {
      inviteGlow.value = false
    }, 560)
  } catch (error: any) {
    uni.showToast({ title: error?.message || '生成失败', icon: 'none' })
  }
}

async function bind() {
  try {
    await request('/couples/bind', { method: 'POST', data: { inviteCode: code.value } })
    uni.showToast({ title: '空间已为你们点亮', icon: 'none' })
    uni.switchTab({ url: '/pages/home/index' })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '配对失败', icon: 'none' })
  }
}
</script>

<style scoped lang="scss">
.block {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
  margin-bottom: 24rpx;
}

.intro {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin: 42rpx 0 30rpx;
}

.headline {
  font-size: 52rpx;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.18;
}

.title {
  font-size: 36rpx;
  font-weight: 600;
  color: var(--color-text);
}

.secret-card {
  min-height: 160rpx;
  border-radius: 22rpx;
  background:
    linear-gradient(135deg, rgba(255, 253, 250, 0.86), rgba(246, 223, 217, 0.62)),
    radial-gradient(circle at 16% 20%, rgba(200, 163, 108, 0.24), transparent 34%);
  border: 1rpx solid rgba(200, 163, 108, 0.28);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
}

.secret-label {
  font-size: 22rpx;
  color: var(--color-rose);
  font-weight: 600;
}

.code {
  font-size: 56rpx;
  letter-spacing: 6rpx;
  font-weight: 600;
  color: var(--color-cocoa);
}
</style>

<template>
  <view class="page romance-bg pair-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="intro">
        <text class="romance-kicker">A LITTLE CEREMONY</text>
        <text class="headline">点亮你们的空间</text>
        <text class="muted">一次配对，一处只属于两个人的私密角落。</text>
      </view>

      <view class="steps">
        <view v-for="step in steps" :key="step.title" class="step" :class="{ active: step.active }">
          <text class="step-index">{{ step.index }}</text>
          <view>
            <text class="step-title">{{ step.title }}</text>
            <text class="muted">{{ step.desc }}</text>
          </view>
        </view>
      </view>

      <view class="card glass-card block" :class="{ 'tap-glow': inviteGlow }">
        <view class="block-head">
          <view>
            <text class="title">把这串密语交给 TA</text>
            <text class="muted">邀请码 24 小时有效，绑定后这里就只对你们两个人可见。</text>
          </view>
          <text v-if="expiresText" class="expire-pill">{{ expiresText }}</text>
        </view>
        <view class="button" :class="{ disabled: creatingInvite }" @click="createInvite">
          {{ creatingInvite ? '正在生成' : inviteCode ? '重新生成密语' : '生成配对密语' }}
        </view>
        <view v-if="inviteCode" class="secret-card pulse-soft">
          <text class="secret-label">OUR SECRET</text>
          <text class="code">{{ inviteCode }}</text>
          <view class="secret-actions">
            <view class="ghost-button compact" @click="copyInvite">复制</view>
            <view class="ghost-button compact" @click="fillInvite">填入下方</view>
          </view>
        </view>
        <text v-if="inviteCode" class="muted">把密语发给对方；演示时也可以填到下面完成配对。</text>
      </view>

      <view class="card glass-card block">
        <view class="block-head">
          <view>
            <text class="title">我收到了密语</text>
            <text class="muted">输入对方发来的邀请码，完成后会进入你们的首页。</text>
          </view>
          <text class="code-hint">{{ normalizedCode.length }}/8</text>
        </view>
        <input class="input code-input" v-model="code" maxlength="12" placeholder="输入对方的邀请码" @input="normalizeInput" />
        <view class="button" :class="{ disabled: !canBind || binding }" @click="bind">
          {{ binding ? '正在配对' : '完成配对' }}
        </view>
        <view class="ghost-button" @click="goHome">先去首页看看</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { request } from '@/api/client'

const inviteCode = ref('')
const code = ref('')
const inviteExpiresAt = ref('')
const inviteGlow = ref(false)
const creatingInvite = ref(false)
const binding = ref(false)

const normalizedCode = computed(() => code.value.trim().toUpperCase())
const canBind = computed(() => normalizedCode.value.length >= 6)
const expiresText = computed(() => {
  if (!inviteExpiresAt.value) return ''
  const expiresAt = new Date(inviteExpiresAt.value).getTime()
  if (Number.isNaN(expiresAt)) return '24 小时有效'
  const minutes = Math.max(0, Math.ceil((expiresAt - Date.now()) / 60000))
  if (minutes <= 0) return '已过期'
  if (minutes < 60) return `${minutes} 分钟后过期`
  return `${Math.ceil(minutes / 60)} 小时内有效`
})
const steps = computed(() => [
  { index: '1', title: '生成密语', desc: inviteCode.value ? '密语已经准备好' : '先生成一串只用一次的邀请', active: Boolean(inviteCode.value) },
  { index: '2', title: '交给对方', desc: inviteCode.value ? '复制后发给 TA' : '让 TA 在自己的手机里输入', active: Boolean(inviteCode.value) },
  { index: '3', title: '完成配对', desc: canBind.value ? '现在可以点亮空间' : '输入密语后即可继续', active: canBind.value }
])

async function createInvite() {
  if (creatingInvite.value) return
  creatingInvite.value = true
  try {
    const data = await request<{ inviteCode: string; expiresAt?: string }>('/couples/invite', { method: 'POST' })
    inviteCode.value = data.inviteCode
    inviteExpiresAt.value = data.expiresAt || ''
    code.value = data.inviteCode
    inviteGlow.value = true
    uni.showToast({ title: '邀请码已生成', icon: 'none' })
    setTimeout(() => {
      inviteGlow.value = false
    }, 560)
  } catch (error: any) {
    uni.showToast({ title: error?.message || '生成失败', icon: 'none' })
  } finally {
    creatingInvite.value = false
  }
}

async function bind() {
  if (!canBind.value || binding.value) {
    uni.showToast({ title: '先输入完整的邀请码', icon: 'none' })
    return
  }
  binding.value = true
  try {
    await request('/couples/bind', { method: 'POST', data: { inviteCode: normalizedCode.value } })
    uni.showToast({ title: '空间已为你们点亮', icon: 'none' })
    uni.switchTab({ url: '/pages/home/index' })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '配对失败', icon: 'none' })
  } finally {
    binding.value = false
  }
}

function copyInvite() {
  if (!inviteCode.value) return
  uni.setClipboardData({
    data: inviteCode.value,
    success: () => uni.showToast({ title: '密语已复制', icon: 'none' })
  })
}

function fillInvite() {
  code.value = inviteCode.value
  uni.showToast({ title: '已填入下方', icon: 'none' })
}

function normalizeInput(event: any) {
  code.value = String(event.detail?.value || '').trim().toUpperCase()
}

function goHome() {
  uni.switchTab({ url: '/pages/home/index' })
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

.steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14rpx;
  margin-bottom: 24rpx;
}

.step {
  min-height: 132rpx;
  border: 1rpx solid var(--color-line);
  border-radius: 18rpx;
  background: rgba(255, 253, 250, 0.56);
  padding: 18rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.step.active {
  border-color: rgba(143, 77, 77, 0.3);
  background: rgba(255, 253, 250, 0.82);
  box-shadow: 0 12rpx 30rpx rgba(143, 77, 77, 0.1);
}

.step-index {
  width: 42rpx;
  height: 42rpx;
  border-radius: 50%;
  background: rgba(143, 77, 77, 0.12);
  color: var(--color-rose);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 700;
}

.step-title {
  display: block;
  font-size: 26rpx;
  color: var(--color-text);
  font-weight: 600;
  margin-bottom: 4rpx;
}

.block-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
}

.block-head > view {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.expire-pill,
.code-hint {
  flex: 0 0 auto;
  border-radius: 999rpx;
  padding: 10rpx 16rpx;
  background: rgba(200, 163, 108, 0.16);
  color: var(--color-cocoa);
  font-size: 22rpx;
  font-weight: 600;
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
  padding: 28rpx;
  box-sizing: border-box;
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

.secret-actions {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14rpx;
  margin-top: 10rpx;
}

.ghost-button.compact {
  height: 68rpx;
  font-size: 26rpx;
}

.button.disabled {
  opacity: 0.48;
}

.code-input {
  font-weight: 700;
  letter-spacing: 4rpx;
  color: var(--color-cocoa);
}

@media (max-width: 360px) {
  .steps {
    grid-template-columns: 1fr;
  }

  .block-head {
    flex-direction: column;
  }
}
</style>

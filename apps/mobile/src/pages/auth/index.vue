<template>
  <view class="page romance-bg auth-page">
    <view class="ambient-glow" />
    <view class="content-layer auth-content">
      <view class="brand">
        <text class="romance-kicker">TONIGHT, CLOSER</text>
        <text class="name">近处</text>
        <text class="sub">今晚，也离你近一点</text>
      </view>

      <view class="card glass-card form" :class="{ 'tap-glow': glow }">
        <text class="form-title">只给你和我看见</text>
        <input class="input" v-model="phone" placeholder="手机号（开发登录）" />
        <input class="input" v-model="nickname" placeholder="昵称" />
        <view class="button" :class="{ disabled: !canLogin || loading }" @click="login">
          {{ loading ? '正在靠近...' : '进入我们的空间' }}
        </view>
        <text class="hint">一段不公开的日常，只在这里轻轻发生。</text>
        <text v-if="errorText" class="error">{{ errorText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useSessionStore } from '@/stores/session'
import { getErrorMessage } from '@/api/client'

const session = useSessionStore()
const phone = ref('13800000001')
const nickname = ref('我')
const loading = ref(false)
const errorText = ref('')
const glow = ref(false)

const canLogin = computed(() => isValidPhone(phone.value) && nickname.value.trim().length > 0)

async function login() {
  if (loading.value) return
  if (!isValidPhone(phone.value)) {
    errorText.value = '请输入 11 位手机号'
    uni.showToast({ title: errorText.value, icon: 'none' })
    return
  }
  if (!nickname.value.trim()) {
    errorText.value = '先写一个昵称'
    uni.showToast({ title: errorText.value, icon: 'none' })
    return
  }
  loading.value = true
  errorText.value = ''
  glow.value = true
  try {
    await session.mockLogin(phone.value.trim(), nickname.value.trim())
    if (session.paired) {
      uni.showToast({ title: '欢迎回来', icon: 'none' })
      uni.switchTab({ url: '/pages/home/index' })
    } else {
      uni.showToast({ title: '请先完成配对', icon: 'none' })
      uni.navigateTo({ url: '/pages/pair/index' })
    }
  } catch (error: any) {
    errorText.value = getErrorMessage(error, '登录失败，请稍后再试')
    uni.showToast({ title: errorText.value, icon: 'none' })
  } finally {
    loading.value = false
    setTimeout(() => {
      glow.value = false
    }, 560)
  }
}

function isValidPhone(value: string) {
  return /^1\d{10}$/.test(value.trim())
}
</script>

<style scoped lang="scss">
.auth-page {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.auth-content {
  display: flex;
  min-height: calc(100vh - 48rpx);
  flex-direction: column;
  justify-content: center;
  gap: 54rpx;
}

.brand {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.name {
  font-size: 76rpx;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1;
}

.sub {
  color: var(--color-muted);
  font-size: 32rpx;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.form-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-cocoa);
}

.hint {
  color: var(--color-muted);
  font-size: 24rpx;
  line-height: 1.6;
  text-align: center;
}

.disabled {
  opacity: 0.52;
}

.error {
  color: #9f4b43;
  font-size: 26rpx;
}
</style>

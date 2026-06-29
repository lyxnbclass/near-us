<template>
  <view class="page romance-bg settings-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="hero">
        <text class="eyebrow">空间设置</text>
        <text class="title">把边界也放温柔</text>
        <text class="sub">模块、隐私和关系状态都在这里管理。所有私密内容只属于你们。</text>
      </view>

      <view class="card glass-card relation-card">
        <view class="relation-top">
          <view>
            <text class="card-title">当前关系</text>
            <text class="muted">{{ relationText }}</text>
          </view>
          <text class="status-pill" :class="couple?.status || 'none'">{{ statusText }}</text>
        </view>
        <view v-if="members.length" class="members">
          <view v-for="member in members" :key="member.id" class="member-pill">
            {{ member.nickname || '我们' }}
          </view>
        </view>
        <view v-if="couple?.status === 'unbind_pending'" class="pending-box">
          <text>解绑申请已提交。</text>
          <text class="muted">冷静期内不会继续写入新的情侣空间内容；撤销后会恢复正常。</text>
          <view class="split-actions">
            <view class="ghost-button" :class="{ disabled: relationSaving }" @click="cancelUnbind">
              {{ relationSaving ? '处理中' : '撤销解绑' }}
            </view>
            <view class="danger-button" :class="{ disabled: relationSaving }" @click="confirmUnbind">
              {{ relationSaving ? '处理中' : '确认解除' }}
            </view>
          </view>
        </view>
        <view v-else-if="paired" class="ghost-button" :class="{ disabled: relationSaving }" @click="requestUnbind">
          {{ relationSaving ? '处理中' : '申请解绑' }}
        </view>
        <view v-else class="button" @click="goPair">去配对</view>
      </view>

      <view class="section-head">
        <text>可选模块</text>
      </view>
      <view class="card glass-card module-row" :class="{ pending: moduleSaving }">
        <view>
          <text class="row-title">宠物栏</text>
          <text class="muted desc">{{ moduleSaving ? '正在保存模块状态' : '宠物档案、动态和宠物相册入口' }}</text>
        </view>
        <switch :checked="petEnabled" :disabled="moduleSaving" color="#C9A46A" @change="togglePet" />
      </view>

      <view class="section-head">
        <text>隐私</text>
      </view>
      <view class="card glass-card module-row" @click="goPrivacy">
        <view>
          <text class="row-title">隐私中心</text>
          <text class="muted desc">导出我的数据，或申请注销并删除数据</text>
        </view>
        <text class="arrow">›</text>
      </view>

      <view class="section-head">
        <text>账号</text>
      </view>
      <view class="card glass-card account-row">
        <view>
          <text class="row-title">退出登录</text>
          <text class="muted desc">只清除当前登录态，不删除你们的空间数据。</text>
        </view>
        <view class="ghost-button small" :class="{ disabled: logoutSaving }" @click="logout">
          {{ logoutSaving ? '退出中' : '退出' }}
        </view>
      </view>
      <view class="card glass-card account-row danger-lite">
        <view>
          <text class="row-title">重置演示数据</text>
          <text class="muted desc">恢复默认测试数据，并清除当前登录态。</text>
        </view>
        <view class="danger-button small" :class="{ disabled: resetSaving }" @click="resetDemo">
          {{ resetSaving ? '重置中' : '重置' }}
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '@/api/client'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const petEnabled = ref(false)
const paired = ref(false)
const couple = ref<any | null>(null)
const members = ref<any[]>([])
const moduleSaving = ref(false)
const relationSaving = ref(false)
const logoutSaving = ref(false)
const resetSaving = ref(false)

const statusText = computed(() => {
  if (!paired.value) return '未配对'
  if (couple.value?.status === 'unbind_pending') return '冷静期'
  return '已点亮'
})

const relationText = computed(() => {
  if (!paired.value) return '还没有绑定两人空间'
  if (couple.value?.status === 'unbind_pending') return '关系正在解绑冷静期'
  const names = members.value.map(item => item.nickname).filter(Boolean).join(' 和 ')
  return names ? `${names} 的私密空间` : '你们的私密空间正在运行'
})

onShow(load)

async function load() {
  const relation = await request<any>('/couples/me')
  paired.value = Boolean(relation.paired)
  couple.value = relation.couple || null
  members.value = relation.members || []
  if (paired.value && couple.value?.status === 'active') {
    const modules = await request<any[]>('/modules')
    petEnabled.value = modules.some(item => item.module_key === 'pet' && item.enabled)
  } else {
    petEnabled.value = false
  }
}

async function togglePet(event: any) {
  if (moduleSaving.value) return
  const previous = petEnabled.value
  const nextEnabled = Boolean(event.detail.value)
  if (!paired.value || couple.value?.status !== 'active') {
    uni.showToast({ title: '配对正常后才能调整模块', icon: 'none' })
    petEnabled.value = previous
    return
  }
  petEnabled.value = nextEnabled
  moduleSaving.value = true
  try {
    await request('/modules/pet', { method: 'PUT', data: { enabled: nextEnabled } })
    uni.showToast({ title: nextEnabled ? '宠物栏已开启' : '宠物栏已关闭', icon: 'none' })
  } catch (error) {
    petEnabled.value = previous
    uni.showToast({ title: '模块状态保存失败', icon: 'none' })
  } finally {
    moduleSaving.value = false
  }
}

function requestUnbind() {
  if (relationSaving.value) return
  uni.showModal({
    title: '申请解绑',
    content: '提交后会进入冷静期，情侣空间将暂时不能继续写入新内容。',
    confirmText: '提交',
    success: async result => {
      if (!result.confirm) return
      relationSaving.value = true
      try {
        await request('/couples/unbind-request', { method: 'POST' })
        uni.showToast({ title: '已进入冷静期', icon: 'none' })
        await load()
      } finally {
        relationSaving.value = false
      }
    }
  })
}

async function cancelUnbind() {
  if (relationSaving.value) return
  relationSaving.value = true
  try {
    await request('/couples/unbind-cancel', { method: 'POST' })
    uni.showToast({ title: '已撤销解绑', icon: 'none' })
    await load()
  } finally {
    relationSaving.value = false
  }
}

function confirmUnbind() {
  if (relationSaving.value) return
  uni.showModal({
    title: '确认解除关系',
    content: '确认后双方会退出当前空间，之后需要重新配对才能继续使用情侣空间。',
    confirmText: '确认解除',
    confirmColor: '#9E4D43',
    success: async result => {
      if (!result.confirm) return
      relationSaving.value = true
      try {
        await request('/couples/unbind-confirm', { method: 'POST' })
        uni.showToast({ title: '已解除配对', icon: 'none' })
        paired.value = false
        couple.value = null
        members.value = []
        uni.navigateTo({ url: '/pages/pair/index' })
      } finally {
        relationSaving.value = false
      }
    }
  })
}

function goPair() {
  uni.navigateTo({ url: '/pages/pair/index' })
}

function goPrivacy() {
  uni.navigateTo({ url: '/pages/privacy/index' })
}

function logout() {
  if (logoutSaving.value) return
  uni.showModal({
    title: '退出登录？',
    content: '退出后需要重新登录才能进入情侣空间，本地测试数据会保留。',
    confirmText: '退出',
    success: result => {
      if (!result.confirm) return
      logoutSaving.value = true
      session.logout()
      uni.showToast({ title: '已退出登录', icon: 'none' })
      uni.navigateTo({ url: '/pages/auth/index' })
      logoutSaving.value = false
    }
  })
}

function resetDemo() {
  if (resetSaving.value) return
  uni.showModal({
    title: '重置演示数据？',
    content: '这会恢复默认测试数据，并清除当前登录态。你新增的本地演示内容会被覆盖。',
    confirmText: '重置',
    confirmColor: '#9E4D43',
    success: result => {
      if (!result.confirm) return
      resetSaving.value = true
      session.resetDemo()
      uni.showToast({ title: '演示数据已重置', icon: 'none' })
      uni.navigateTo({ url: '/pages/auth/index' })
      resetSaving.value = false
    }
  })
}
</script>

<style scoped lang="scss">
.settings-page {
  min-height: 100vh;
}

.content-layer {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 24rpx;
}

.hero {
  display: grid;
  gap: 12rpx;
  padding: 28rpx 6rpx 4rpx;
}

.eyebrow,
.muted {
  color: var(--color-muted);
}

.eyebrow {
  font-size: 24rpx;
}

.title {
  color: var(--color-text);
  font-size: 48rpx;
  font-weight: 700;
}

.sub {
  color: var(--color-soft);
  font-size: 27rpx;
  line-height: 1.65;
}

.relation-card,
.pending-box {
  display: grid;
  gap: 20rpx;
}

.relation-top,
.module-row,
.account-row,
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.module-row.pending {
  opacity: 0.76;
}

.card-title,
.row-title,
.section-head {
  color: var(--color-text);
  font-size: 30rpx;
  font-weight: 700;
}

.status-pill,
.member-pill {
  flex: 0 0 auto;
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  color: var(--color-text);
  font-size: 24rpx;
  font-weight: 700;
  background: rgba(201, 164, 106, 0.18);
}

.status-pill.unbind_pending {
  background: rgba(158, 77, 67, 0.14);
  color: #9e4d43;
}

.status-pill.none {
  background: rgba(46, 42, 39, 0.08);
}

.members {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.pending-box {
  padding: 22rpx;
  border-radius: 18rpx;
  background: rgba(158, 77, 67, 0.08);
}

.split-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}

.ghost-button,
.danger-button {
  min-height: 84rpx;
  border-radius: 18rpx;
  display: grid;
  place-items: center;
  font-weight: 700;
}

.ghost-button {
  color: var(--color-text);
  border: 1rpx solid rgba(46, 42, 39, 0.12);
  background: rgba(255, 253, 252, 0.58);
}

.danger-button {
  color: #fff;
  background: #9e4d43;
}

.danger-lite {
  border-color: rgba(158, 77, 67, 0.22);
}

.ghost-button.disabled,
.danger-button.disabled {
  opacity: 0.52;
}

.ghost-button.small,
.danger-button.small {
  flex: 0 0 auto;
  min-width: 128rpx;
  min-height: 64rpx;
  padding: 0 24rpx;
}

.desc {
  display: block;
  margin-top: 8rpx;
  font-size: 25rpx;
  line-height: 1.5;
}

.arrow {
  color: var(--color-rose);
  font-size: 48rpx;
}
</style>

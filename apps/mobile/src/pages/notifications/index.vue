<template>
  <view class="page romance-bg notifications-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="intro">
        <text class="romance-kicker">SOFT NOTICES</text>
        <text class="headline">那些轻轻抵达你的消息</text>
        <text class="muted">不催促，只提醒你：有人刚刚靠近了一点。</text>
      </view>

      <view class="card glass-card summary">
        <view>
          <text class="summary-number">{{ unreadCount }}</text>
          <text class="muted">条未读消息</text>
        </view>
        <view class="ghost-button" :class="{ disabled: !unreadCount || markingAll }" @click="markAllRead">
          {{ markingAll ? '处理中' : '全部已读' }}
        </view>
      </view>

      <view class="segmented">
        <view
          v-for="option in filters"
          :key="option.key"
          class="segment"
          :class="{ active: activeFilter === option.key }"
          @click="activeFilter = option.key"
        >
          {{ option.label }}
        </view>
      </view>

      <view class="stack">
        <view v-for="item in filteredNotifications" :key="item.id" class="card glass-card notice" :class="{ unread: !item.read_at }" @click="markRead(item)">
          <view class="notice-dot" />
          <view class="notice-main">
            <text class="notice-title">{{ item.title }}</text>
            <text class="muted">{{ item.body || typeText(item.type) }}</text>
            <text class="tiny">{{ formatTime(item.created_at) }}</text>
          </view>
          <text class="status-pill">{{ item.read_at ? '已读' : '未读' }}</text>
        </view>

        <view v-if="!filteredNotifications.length" class="card glass-card empty">
          <text>{{ emptyTitle }}</text>
          <text class="muted">{{ emptyText }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getErrorMessage, request } from '@/api/client'

const notifications = ref<any[]>([])
const activeFilter = ref<'all' | 'unread' | 'read'>('all')
const markingAll = ref(false)

const filters = [
  { key: 'all', label: '全部' },
  { key: 'unread', label: '未读' },
  { key: 'read', label: '已读' }
] as const

const unreadCount = computed(() => notifications.value.filter(item => !item.read_at).length)
const filteredNotifications = computed(() => {
  if (activeFilter.value === 'unread') return notifications.value.filter(item => !item.read_at)
  if (activeFilter.value === 'read') return notifications.value.filter(item => item.read_at)
  return notifications.value
})

const emptyTitle = computed(() => {
  if (activeFilter.value === 'unread') return '没有未读消息'
  if (activeFilter.value === 'read') return '还没有已读消息'
  return '暂时没有新消息'
})

const emptyText = computed(() => {
  if (activeFilter.value === 'unread') return '很好，所有靠近都已经被你看见。'
  if (activeFilter.value === 'read') return '读过的消息会留在这里。'
  return '有新的互动时，这里会亮起来。'
})

onShow(load)

async function load() {
  try {
    notifications.value = await request('/notifications')
  } catch (error: any) {
    uni.showToast({ title: getErrorMessage(error, '暂时加载不了通知'), icon: 'none' })
  }
}

async function markRead(item: any) {
  if (item.read_at) return
  try {
    await request(`/notifications/${item.id}/read`, { method: 'POST' })
    uni.showToast({ title: '已标记为已读', icon: 'none' })
    await load()
  } catch (error: any) {
    uni.showToast({ title: getErrorMessage(error, '暂时标记不了这条通知'), icon: 'none' })
  }
}

async function markAllRead() {
  if (!unreadCount.value || markingAll.value) return
  markingAll.value = true
  try {
    await request('/notifications/read-all', { method: 'POST' })
    uni.showToast({ title: '未读已清空', icon: 'none' })
    await load()
  } catch (error: any) {
    uni.showToast({ title: getErrorMessage(error, '暂时清空不了未读'), icon: 'none' })
  } finally {
    markingAll.value = false
  }
}

function typeText(type: string) {
  const map: Record<string, string> = {
    'status.created': '对方分享了新的此刻',
    'status.reacted': '对方回应了你的此刻',
    'affection.created': '对方留下了一张心意卡片'
  }
  return map[type] || '有一条新的站内消息'
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}
</script>

<style scoped lang="scss">
.intro,
.summary,
.stack {
  display: grid;
  gap: 18rpx;
}

.intro {
  margin: 28rpx 0 24rpx;
}

.headline {
  font-size: 44rpx;
  font-weight: 600;
  line-height: 1.25;
  color: var(--color-text);
}

.summary {
  grid-template-columns: 1fr 190rpx;
  align-items: center;
}

.summary-number {
  display: block;
  color: var(--color-cocoa);
  font-size: 52rpx;
  font-weight: 800;
  line-height: 1.1;
}

.ghost-button {
  min-height: 74rpx;
  border-radius: 999rpx;
  display: grid;
  place-items: center;
  color: var(--color-text);
  border: 1rpx solid var(--color-line);
  background: rgba(255, 253, 252, 0.58);
  font-size: 25rpx;
  font-weight: 700;
}

.ghost-button.disabled {
  opacity: 0.46;
}

.segmented {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10rpx;
  padding: 8rpx;
  border-radius: 18rpx;
  background: rgba(46, 42, 39, 0.06);
}

.segment {
  min-height: 72rpx;
  display: grid;
  place-items: center;
  border-radius: 14rpx;
  color: var(--color-muted);
  font-size: 25rpx;
  font-weight: 700;
}

.segment.active {
  background: rgba(255, 253, 252, 0.86);
  color: var(--color-text);
  box-shadow: 0 14rpx 28rpx rgba(46, 42, 39, 0.08);
}

.notice {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.notice.unread {
  box-shadow: var(--shadow-soft), 0 0 36rpx rgba(217, 167, 160, 0.22);
}

.notice-dot {
  flex: 0 0 auto;
  width: 18rpx;
  height: 18rpx;
  border-radius: 50%;
  background: var(--color-rose);
  box-shadow: 0 0 28rpx rgba(143, 77, 77, 0.42);
}

.notice-main {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 8rpx;
}

.notice-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-cocoa);
}

.tiny {
  color: var(--color-rose);
  font-size: 24rpx;
  font-weight: 600;
}

.status-pill {
  flex: 0 0 auto;
  border-radius: 999rpx;
  padding: 10rpx 16rpx;
  color: var(--color-rose);
  background: rgba(217, 167, 160, 0.16);
  font-size: 24rpx;
}

.empty {
  display: grid;
  gap: 8rpx;
  text-align: center;
}
</style>

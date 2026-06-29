<template>
  <view class="page romance-bg notifications-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="intro">
        <text class="romance-kicker">SOFT NOTICES</text>
        <text class="headline">那些轻轻抵达你的消息</text>
        <text class="muted">不催促，只提醒你：有人刚刚靠近了一点。</text>
      </view>

      <view class="stack">
        <view v-for="item in notifications" :key="item.id" class="card glass-card notice" :class="{ unread: !item.read_at, pending: readingId === item.id }" @click="markRead(item)">
          <view class="notice-dot" />
          <view class="notice-main">
            <text class="notice-title">{{ item.title }}</text>
            <text class="muted">{{ item.body || typeText(item.type) }}</text>
            <text class="tiny">{{ formatTime(item.created_at) }}</text>
          </view>
          <text class="status-pill">{{ readingId === item.id ? '处理中' : item.read_at ? '已读' : '未读' }}</text>
        </view>

        <view v-if="!notifications.length" class="card glass-card empty">
          <text class="muted">暂时没有新消息。</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '@/api/client'
import { ensurePairedSpace } from '@/utils/spaceGuard'

const notifications = ref<any[]>([])
const readingId = ref<number | null>(null)

onShow(load)

async function load() {
  if (!(await ensurePairedSpace())) return
  try {
    notifications.value = await request('/notifications')
  } catch (error: any) {
    uni.showToast({ title: error?.message || '加载通知失败', icon: 'none' })
  }
}

async function markRead(item: any) {
  if (item.read_at || readingId.value) return
  readingId.value = item.id
  try {
    await request(`/notifications/${item.id}/read`, { method: 'POST' })
    uni.showToast({ title: '已标记为已读', icon: 'none' })
    await load()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '标记已读失败', icon: 'none' })
  } finally {
    readingId.value = null
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

.notice {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.notice.unread {
  box-shadow: var(--shadow-soft), 0 0 36rpx rgba(217, 167, 160, 0.22);
}

.notice.pending {
  opacity: 0.72;
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
  text-align: center;
}
</style>

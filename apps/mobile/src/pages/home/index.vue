<template>
  <view class="page romance-bg home-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="hero glass-card">
        <view class="hero-top">
          <view>
            <text class="eyebrow">我们的空间</text>
            <text class="hero-title">{{ greeting }}</text>
          </view>
          <view class="notice-chip" @click="go('/pages/notifications/index')">
            <text>{{ home?.unreadCount || 0 }}</text>
            <text>未读</text>
          </view>
        </view>
        <view class="presence">
          <view class="presence-dot" />
          <view class="presence-copy">
            <text class="status">{{ home?.partnerStatus?.content || '还没有对方的新状态' }}</text>
            <text class="muted">{{ home?.partnerStatus?.nickname || '配对后这里会出现对方的近况' }}</text>
          </view>
        </view>
      </view>

      <view class="stats-grid">
        <view class="stat-card glass-card">
          <text class="stat-value">{{ home?.togetherDays || 0 }}</text>
          <text class="stat-label">在一起的第几天</text>
        </view>
        <view class="stat-card glass-card" @click="go('/pages/future/index')">
          <text class="stat-value">{{ home?.unlockedFutureLetterCount || 0 }}/{{ home?.futureLetterCount || 0 }}</text>
          <text class="stat-label">可读未来信</text>
        </view>
      </view>

      <view class="section-title">今日概览</view>
      <view class="insight-grid">
        <view class="insight-card glass-card" @click="go('/pages/interactions/index')">
          <text class="insight-kicker">今日话题</text>
          <text class="insight-title">{{ home?.dailyTopic?.question || '今晚想和对方聊点什么？' }}</text>
          <text class="muted">{{ topicStateText }}</text>
        </view>
        <view class="insight-card glass-card" @click="go('/pages/interactions/index')">
          <text class="insight-kicker">共同愿望</text>
          <text class="insight-title">{{ home?.wishProgress?.next?.title || '还没有共同愿望' }}</text>
          <text class="muted">{{ wishProgressText }}</text>
        </view>
      </view>

      <view class="section-head">
        <text>临近纪念日</text>
        <text class="head-action" @click="go('/pages/anniversary/index')">管理</text>
      </view>
      <view class="date-strip">
        <view v-for="item in home?.anniversaries || []" :key="item.id" class="date-card glass-card" @click="go('/pages/anniversary/index')">
          <text class="date-days">{{ item.days_left === 0 ? '今天' : `${item.days_left ?? '-'} 天` }}</text>
          <text class="date-title">{{ item.title }}</text>
          <text class="muted">{{ item.event_date }}</text>
        </view>
        <view v-if="!home?.anniversaries?.length" class="date-card glass-card" @click="go('/pages/anniversary/index')">
          <text class="date-days">添加</text>
          <text class="date-title">还没有纪念日</text>
          <text class="muted">把重要日期放进来</text>
        </view>
      </view>

      <view class="section-head">
        <text>最近内容</text>
        <text class="head-action" @click="go('/pages/memories/index')">回忆</text>
      </view>
      <view class="recent-grid">
        <view class="recent-card glass-card" @click="go('/pages/status/index')">
          <text class="insight-kicker">最新此刻</text>
          <text class="recent-title">{{ home?.latestStatus?.content || '还没有新的此刻' }}</text>
          <text class="muted">{{ home?.latestStatus?.mood_tag || '发一句近况给 TA' }}</text>
        </view>
        <view class="recent-card glass-card" @click="go('/pages/album/index')">
          <text class="insight-kicker">相册</text>
          <text class="recent-title">{{ home?.latestAlbum?.caption || '相册还在等第一张照片' }}</text>
          <text class="muted">{{ home?.latestAlbum?.creator_name || '把今天收藏起来' }}</text>
        </view>
      </view>

      <view class="section-title">快捷动作</view>
      <view class="quick">
        <view v-for="action in quickActions" :key="action.path" class="quick-action glass-card" @click="go(action.path)">
          <text class="quick-icon">{{ action.icon }}</text>
          <view>
            <text class="quick-title">{{ action.title }}</text>
            <text class="muted">{{ action.desc }}</text>
          </view>
        </view>
        <view
          v-for="mod in enabledModules"
          :key="mod.key"
          class="quick-action glass-card"
          @click="go(mod.homePath)"
        >
          <text class="quick-icon">P</text>
          <view>
            <text class="quick-title">{{ mod.name }}</text>
            <text class="muted">打开可选模块</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '@/api/client'
import { moduleRegistry } from '@/modules/registry'

const home = ref<any>(null)

const quickActions = [
  { title: '告诉 TA 此刻', desc: '分享文字或照片', icon: 'S', path: '/pages/status/index' },
  { title: '心动小事', desc: '话题、卡片和愿望', icon: 'T', path: '/pages/interactions/index' },
  { title: '写给未来', desc: '存一封到时再读的信', icon: 'F', path: '/pages/future/index' },
  { title: '藏一个日期', desc: '纪念日和重逢提醒', icon: 'D', path: '/pages/anniversary/index' }
]

const enabledModules = computed(() => {
  const keys = (home.value?.enabledModules || []).map((item: any) => item.module_key)
  return moduleRegistry.filter(item => keys.includes(item.key))
})

const greeting = computed(() => {
  const days = home.value?.togetherDays || 0
  return days ? `第 ${days} 天，也要好好靠近` : '把普通日子慢慢藏好'
})

const topicStateText = computed(() => {
  if (home.value?.dailyTopic?.unlocked) return '双方答案已经解锁'
  if (home.value?.dailyTopic?.answeredByMe) return '已回答，等 TA 一下'
  return '还没有回答今天的问题'
})

const wishProgressText = computed(() => {
  const progress = home.value?.wishProgress
  if (!progress?.total) return '先许下一个小愿望'
  return `已完成 ${progress.completed}/${progress.total}`
})

onShow(load)

async function load() {
  try {
    home.value = await request('/home')
  } catch (error: any) {
    if (error.message === 'PAIR_REQUIRED') {
      uni.navigateTo({ url: '/pages/pair/index' })
    } else {
      uni.navigateTo({ url: '/pages/auth/index' })
    }
  }
}

function go(url: string) {
  const tabPages = ['/pages/home/index', '/pages/album/index', '/pages/status/index', '/pages/diary/index', '/pages/settings/index']
  if (tabPages.includes(url)) {
    uni.switchTab({ url })
  } else {
    uni.navigateTo({ url })
  }
}
</script>

<style scoped lang="scss">
.hero {
  position: relative;
  min-height: 360rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 34rpx;
  padding: 40rpx;
  border-radius: 28rpx;
  overflow: hidden;
}

.hero::after {
  content: "";
  position: absolute;
  right: -80rpx;
  top: -90rpx;
  width: 260rpx;
  height: 260rpx;
  border-radius: 50%;
  background: rgba(217, 167, 160, 0.24);
  filter: blur(6rpx);
}

.presence-dot {
  flex: 0 0 auto;
  width: 22rpx;
  height: 22rpx;
  border-radius: 50%;
  background: var(--color-rose);
  box-shadow: 0 0 32rpx rgba(143, 77, 77, 0.45);
  animation: heartbeat 1.4s ease-in-out infinite;
}

.hero-top,
.presence,
.section-head,
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.hero-title {
  display: block;
  margin-top: 12rpx;
  color: var(--color-text);
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1.25;
}

.notice-chip {
  flex: 0 0 auto;
  width: 104rpx;
  height: 104rpx;
  border-radius: 50%;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 2rpx;
  color: var(--color-rose);
  background: rgba(255, 253, 250, 0.66);
  border: 1rpx solid var(--color-line);
  font-size: 22rpx;
  font-weight: 700;
}

.presence {
  justify-content: flex-start;
}

.presence-copy {
  min-width: 0;
  display: grid;
  gap: 10rpx;
}

.eyebrow {
  color: var(--color-rose);
  font-size: 24rpx;
  font-weight: 600;
}

.status {
  position: relative;
  z-index: 1;
  font-size: 36rpx;
  font-weight: 600;
  line-height: 1.35;
  color: var(--color-text);
}

.stats-grid,
.insight-grid,
.recent-grid,
.quick {
  display: grid;
  gap: 16rpx;
}

.stats-grid,
.insight-grid,
.recent-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.stat-card,
.insight-card,
.recent-card,
.date-card,
.quick-action {
  border-radius: 20rpx;
  padding: 24rpx;
  box-sizing: border-box;
}

.stat-card,
.insight-card,
.recent-card {
  display: grid;
  gap: 10rpx;
}

.stat-value {
  font-size: 48rpx;
  font-weight: 600;
  color: var(--color-cocoa);
}

.stat-label,
.head-action {
  color: var(--color-muted);
  font-size: 24rpx;
}

.section-head {
  margin: 34rpx 0 16rpx;
  color: var(--color-text);
  font-size: 32rpx;
  font-weight: 600;
}

.head-action {
  color: var(--color-rose);
  font-weight: 700;
}

.insight-title,
.recent-title,
.date-title,
.quick-title {
  color: var(--color-text);
  font-weight: 700;
  line-height: 1.45;
}

.insight-kicker {
  color: var(--color-rose);
  font-size: 23rpx;
  font-weight: 700;
}

.date-strip {
  display: flex;
  gap: 16rpx;
  overflow-x: auto;
  padding-bottom: 4rpx;
}

.date-card {
  flex: 0 0 260rpx;
  display: grid;
  gap: 8rpx;
}

.date-days {
  color: var(--color-rose);
  font-size: 34rpx;
  font-weight: 800;
}

.quick {
  grid-template-columns: 1fr;
}

.quick-action {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.quick-icon {
  flex: 0 0 auto;
  width: 72rpx;
  height: 72rpx;
  border-radius: 22rpx;
  display: grid;
  place-items: center;
  color: #fffdfc;
  background: linear-gradient(135deg, var(--color-cocoa), var(--color-rose));
  font-size: 28rpx;
  font-weight: 800;
}

@keyframes heartbeat {
  0%, 100% { transform: scale(1); opacity: 0.78; }
  45% { transform: scale(1.28); opacity: 1; }
}
</style>

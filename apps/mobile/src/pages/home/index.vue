<template>
  <view class="page romance-bg home-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="hero glass-card pulse-soft">
        <view class="presence-dot" />
        <text class="eyebrow">TA 的此刻</text>
        <text class="status">{{ home?.partnerStatus?.content || '还没有对方的新状态' }}</text>
        <text class="muted">{{ home?.partnerStatus?.nickname || '配对后这里会出现对方的近况' }}</text>
      </view>

      <view class="days glass-card">
        <view>
          <text class="number">{{ home?.togetherDays || 0 }}</text>
          <text class="unit">天</text>
        </view>
        <text class="muted">把普通日子，慢慢藏成我们的纪念。</text>
      </view>

      <view class="section-title">临近纪念日</view>
      <view class="card glass-card list">
        <view v-for="item in home?.anniversaries || []" :key="item.id" class="row">
          <text>{{ item.title }}</text>
          <text class="muted">{{ item.days_left === 0 ? '就是今天' : `还有 ${item.days_left ?? '-'} 天` }}</text>
        </view>
        <text v-if="!home?.anniversaries?.length" class="muted">还没有添加纪念日</text>
      </view>

      <view class="section-title">轻轻靠近</view>
      <view class="quick">
        <view class="ghost-button" @click="go('/pages/status/index')">告诉 TA 此刻</view>
        <view class="ghost-button" @click="go('/pages/interactions/index')">心动小事</view>
        <view class="ghost-button" @click="go('/pages/future/index')">写给未来</view>
        <view class="ghost-button" @click="go('/pages/memories/index')">今日回忆</view>
        <view class="ghost-button" @click="go('/pages/notifications/index')">通知</view>
        <view class="ghost-button" @click="go('/pages/anniversary/index')">藏一个日期</view>
        <view
          v-for="mod in enabledModules"
          :key="mod.key"
          class="ghost-button"
          @click="go(mod.homePath)"
        >
          {{ mod.name }}
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

const enabledModules = computed(() => {
  const keys = (home.value?.enabledModules || []).map((item: any) => item.module_key)
  return moduleRegistry.filter(item => keys.includes(item.key))
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
  justify-content: center;
  gap: 20rpx;
  padding: 40rpx;
  border-radius: 30rpx;
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
  width: 22rpx;
  height: 22rpx;
  border-radius: 50%;
  background: var(--color-rose);
  box-shadow: 0 0 32rpx rgba(143, 77, 77, 0.45);
  animation: heartbeat 1.4s ease-in-out infinite;
}

.eyebrow {
  color: var(--color-rose);
  font-size: 24rpx;
  font-weight: 600;
}

.status {
  position: relative;
  z-index: 1;
  font-size: 46rpx;
  font-weight: 600;
  line-height: 1.35;
  color: var(--color-text);
}

.days {
  margin-top: 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  border-radius: 28rpx;
  padding: 28rpx;
  box-sizing: border-box;
}

.number {
  font-size: 64rpx;
  font-weight: 600;
  color: var(--color-cocoa);
}

.unit {
  margin-left: 8rpx;
  color: var(--color-muted);
}

.list,
.quick {
  display: grid;
  gap: 16rpx;
}

.quick {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 16rpx;
}

@keyframes heartbeat {
  0%, 100% { transform: scale(1); opacity: 0.78; }
  45% { transform: scale(1.28); opacity: 1; }
}
</style>

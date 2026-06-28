<template>
  <view class="page romance-bg memories-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="intro">
        <text class="romance-kicker">ON THIS DAY</text>
        <text class="headline">今天，曾经也有我们</text>
        <text class="muted">{{ memory?.date || '' }} · 只唤醒属于你们的旧时刻</text>
      </view>

      <view class="card glass-card summary pulse-soft">
        <text class="summary-number">{{ totalCount }}</text>
        <text class="muted">条回忆被轻轻翻到今天</text>
      </view>

      <view class="section-title">照片里的今天</view>
      <view class="stack">
        <view v-for="item in memory?.albums || []" :key="item.id" class="card glass-card memory-card">
          <view class="photo-wash">
            <text>{{ item.mime_type || 'memory' }}</text>
          </view>
          <view class="memory-text">
            <text class="memory-title">{{ item.caption || '没有注解，却还记得那天。' }}</text>
            <text class="muted">{{ item.creator_name }} · {{ formatTime(item.taken_at || item.created_at) }}</text>
          </view>
        </view>
        <view v-if="!memory?.albums?.length" class="card glass-card empty">
          <text class="muted">照片还没有在今天重逢。</text>
        </view>
      </view>

      <view class="section-title">日记里的今天</view>
      <view class="stack">
        <view v-for="item in memory?.diaries || []" :key="item.id" class="card glass-card diary-card" @click="openDiary(item)">
          <text class="memory-title">{{ item.title }}</text>
          <text class="muted">{{ item.visibility === 'private' ? '仅自己可见' : '双方可见' }} · {{ formatTime(item.created_at) }}</text>
        </view>
        <view v-if="!memory?.diaries?.length" class="card glass-card empty">
          <text class="muted">今天暂时没有被日记叫醒。</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '@/api/client'

const memory = ref<any>(null)

const totalCount = computed(() => {
  return (memory.value?.albums?.length || 0) + (memory.value?.diaries?.length || 0)
})

onShow(load)

async function load() {
  memory.value = await request('/memories/today')
}

function openDiary(item: any) {
  uni.showToast({
    title: item.visibility === 'private' ? '这是只属于你的旧日记' : '这是一篇共享旧日记',
    icon: 'none'
  })
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 10) : ''
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

.summary {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  border-radius: 30rpx;
}

.summary-number {
  color: var(--color-rose);
  font-size: 66rpx;
  font-weight: 600;
}

.memory-card {
  display: grid;
  gap: 18rpx;
}

.photo-wash {
  height: 300rpx;
  border-radius: 22rpx;
  background:
    radial-gradient(circle at 20% 20%, rgba(217, 167, 160, 0.36), transparent 32%),
    linear-gradient(135deg, rgba(255, 253, 250, 0.84), rgba(239, 218, 208, 0.76));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-muted);
}

.memory-text,
.diary-card {
  display: grid;
  gap: 10rpx;
}

.memory-title {
  font-size: 31rpx;
  line-height: 1.5;
  font-weight: 600;
  color: var(--color-cocoa);
}

.empty {
  text-align: center;
}
</style>

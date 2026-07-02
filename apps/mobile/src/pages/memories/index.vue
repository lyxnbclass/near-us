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

      <view class="filter-row">
        <text
          v-for="option in filters"
          :key="option.key"
          class="filter-pill"
          :class="{ active: activeFilter === option.key }"
          @click="activeFilter = option.key"
        >
          {{ option.label }}
        </text>
      </view>

      <view v-if="showAlbums" class="section-head">
        <text>照片里的今天</text>
        <text class="head-action" @click="goAlbum">去相册</text>
      </view>
      <view class="stack">
        <view v-for="item in albums" :key="item.id" class="card glass-card memory-card" @click="goAlbum">
          <image v-if="imageUrl(item)" class="memory-photo" :src="imageUrl(item)" mode="aspectFill" />
          <view v-else class="photo-wash">
            <text>{{ item.mime_type || 'memory' }}</text>
          </view>
          <view class="memory-text">
            <text class="memory-title">{{ item.caption || '没有注解，却还记得那天。' }}</text>
            <text class="muted">{{ yearsText(item) }} · {{ item.creator_name }} · {{ formatTime(item.taken_at || item.created_at) }}</text>
          </view>
        </view>
        <view v-if="showAlbums && !albums.length" class="card glass-card empty">
          <text>照片还没有在今天重逢。</text>
          <text class="muted">可以去相册，把今天也放进时间里。</text>
          <view class="ghost-button" @click="goAlbum">去相册</view>
        </view>
      </view>

      <view v-if="showDiaries" class="section-head">
        <text>日记里的今天</text>
        <text class="head-action" @click="goDiary">写日记</text>
      </view>
      <view class="stack">
        <view v-for="item in diaries" :key="item.id" class="card glass-card diary-card" @click="openDiary(item)">
          <text class="memory-title">{{ item.title }}</text>
          <text class="muted">{{ yearsText(item) }} · {{ item.visibility === 'private' ? '仅自己可见' : '双方可见' }} · {{ formatTime(item.created_at) }}</text>
        </view>
        <view v-if="showDiaries && !diaries.length" class="card glass-card empty">
          <text>今天暂时没有被日记叫醒。</text>
          <text class="muted">写下一页，明年的今天它会回来找你。</text>
          <view class="ghost-button" @click="goDiary">写日记</view>
        </view>
      </view>

      <view v-if="!totalCount" class="card glass-card action-card">
        <text class="memory-title">把今天留给未来</text>
        <text class="muted">从现在开始记录，之后每一年都会多一个可以回望的入口。</text>
        <view class="action-grid">
          <view class="button" @click="goAlbum">传一张照片</view>
          <view class="ghost-button" @click="goDiary">写一页日记</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '@/api/client'
import { ensurePairedSpace } from '@/utils/spaceGuard'

const memory = ref<any>(null)
const activeFilter = ref<'all' | 'albums' | 'diaries'>('all')

const filters = [
  { key: 'all', label: '全部' },
  { key: 'albums', label: '照片' },
  { key: 'diaries', label: '日记' }
] as const

const albums = computed(() => memory.value?.albums || [])
const diaries = computed(() => memory.value?.diaries || [])
const showAlbums = computed(() => activeFilter.value === 'all' || activeFilter.value === 'albums')
const showDiaries = computed(() => activeFilter.value === 'all' || activeFilter.value === 'diaries')

const totalCount = computed(() => {
  return (memory.value?.albums?.length || 0) + (memory.value?.diaries?.length || 0)
})

onShow(load)

async function load() {
  if (!(await ensurePairedSpace())) return
  memory.value = await request('/memories/today')
}

function openDiary(item: any) {
  uni.showToast({ title: item.visibility === 'private' ? '这是只属于你的旧日记' : '这是一篇共享旧日记', icon: 'none' })
  goDiary()
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 10) : ''
}

function imageUrl(item: any) {
  return item.local_url || item.localUrl || item.object_key || ''
}

function yearsText(item: any) {
  const years = Number(item.years_ago || 0)
  return years > 0 ? `${years} 年前` : '曾经'
}

function goAlbum() {
  uni.switchTab({ url: '/pages/album/index' })
}

function goDiary() {
  uni.switchTab({ url: '/pages/diary/index' })
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

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.filter-pill {
  padding: 12rpx 20rpx;
  border-radius: 999rpx;
  color: var(--color-muted);
  border: 1rpx solid var(--color-line);
  background: rgba(255, 253, 252, 0.58);
  font-size: 25rpx;
  font-weight: 700;
}

.filter-pill.active {
  color: var(--color-rose);
  border-color: rgba(143, 77, 77, 0.32);
  background: rgba(217, 167, 160, 0.16);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--color-text);
  font-size: 32rpx;
  font-weight: 700;
}

.head-action {
  color: var(--color-rose);
  font-size: 24rpx;
  font-weight: 700;
}

.memory-card {
  display: grid;
  gap: 18rpx;
}

.memory-photo {
  display: block;
  width: 100%;
  height: 340rpx;
  border-radius: 22rpx;
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
.diary-card,
.action-card {
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
  display: grid;
  gap: 10rpx;
  text-align: center;
}

.ghost-button,
.button {
  min-height: 82rpx;
  border-radius: 20rpx;
  display: grid;
  place-items: center;
  font-weight: 700;
}

.ghost-button {
  color: var(--color-text);
  border: 1rpx solid var(--color-line);
  background: rgba(255, 253, 252, 0.58);
}

.action-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14rpx;
}
</style>

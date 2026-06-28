<template>
  <view class="page romance-bg future-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="intro">
        <text class="romance-kicker">TIME CAPSULE</text>
        <text class="headline">写给未来某一刻的 TA</text>
        <text class="muted">把现在说不完的话，交给一个更合适的时间。</text>
      </view>

      <view class="card glass-card composer" :class="{ 'tap-glow': glow }">
        <input class="input" v-model="title" placeholder="信的标题" />
        <textarea v-model="content" placeholder="写一封只在未来打开的信" />
        <input class="input" v-model="openAt" placeholder="开启时间：2026-08-20 20:00" />
        <view class="button" @click="createLetter">封存这封信</view>
      </view>

      <view class="section-title">时间里的信</view>
      <view class="stack">
        <view v-for="letter in letters" :key="letter.id" class="card glass-card letter-card" :class="{ locked: !letter.unlocked }" @click="openLetter(letter)">
          <view class="seal" :class="{ open: letter.unlocked }" />
          <view class="letter-main">
            <text class="letter-title">{{ letter.title }}</text>
            <text class="muted">{{ letter.unlocked ? '已经可以打开' : '会在约定时间打开' }}</text>
            <text class="tiny">{{ formatTime(letter.open_at) }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '@/api/client'

const title = ref('')
const content = ref('')
const openAt = ref('')
const letters = ref<any[]>([])
const glow = ref(false)

onShow(load)

async function load() {
  letters.value = await request('/future-letters')
}

async function createLetter() {
  if (!title.value.trim() || !content.value.trim() || !openAt.value.trim()) {
    uni.showToast({ title: '标题、内容和时间都要写', icon: 'none' })
    return
  }
  await request('/future-letters', {
    method: 'POST',
    data: {
      title: title.value,
      content: content.value,
      openAt: toIso(openAt.value),
      recipientMode: 'partner'
    }
  })
  title.value = ''
  content.value = ''
  openAt.value = ''
  glow.value = true
  uni.showToast({ title: '信已经封存', icon: 'none' })
  setTimeout(() => {
    glow.value = false
  }, 560)
  await load()
}

async function openLetter(letter: any) {
  try {
    const detail = await request<any>(`/future-letters/${letter.id}`)
    uni.showModal({ title: detail.title, content: detail.content, showCancel: false })
  } catch (error: any) {
    uni.showToast({
      title: error?.message === 'FUTURE_LETTER_LOCKED' ? '还没到打开的时候' : '暂时打不开这封信',
      icon: 'none'
    })
  }
}

function toIso(value: string) {
  return value.replace(' ', 'T').length === 16 ? `${value.replace(' ', 'T')}:00` : value.replace(' ', 'T')
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}
</script>

<style scoped lang="scss">
.intro,
.composer,
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

textarea {
  width: 100%;
  min-height: 220rpx;
  line-height: 1.6;
}

.letter-card {
  display: flex;
  align-items: center;
  gap: 22rpx;
}

.letter-card.locked {
  opacity: 0.86;
}

.seal {
  flex: 0 0 auto;
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(143, 77, 77, 0.86), rgba(200, 163, 108, 0.72));
  box-shadow: 0 0 34rpx rgba(143, 77, 77, 0.22);
  position: relative;
}

.seal::after {
  content: "";
  position: absolute;
  inset: 24rpx;
  border-radius: 50%;
  background: rgba(255, 253, 250, 0.58);
}

.seal.open {
  animation: pulseSoft 1.8s ease-in-out infinite;
}

.letter-main {
  min-width: 0;
  display: grid;
  gap: 8rpx;
}

.letter-title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--color-cocoa);
}

.tiny {
  color: var(--color-rose);
  font-size: 24rpx;
  font-weight: 600;
}
</style>

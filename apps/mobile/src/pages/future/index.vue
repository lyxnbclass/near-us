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
        <view class="recipient-row">
          <view
            class="recipient-pill"
            :class="{ active: recipientMode === 'partner' }"
            @click="recipientMode = 'partner'"
          >
            只给 TA
          </view>
          <view
            class="recipient-pill"
            :class="{ active: recipientMode === 'both' }"
            @click="recipientMode = 'both'"
          >
            两个人一起读
          </view>
        </view>
        <view class="time-grid">
          <picker mode="date" :value="openDate" @change="pickDate">
            <view class="time-picker">
              <text>{{ openDate || '选择日期' }}</text>
              <text class="muted">打开日期</text>
            </view>
          </picker>
          <picker mode="time" :value="openTime" @change="pickTime">
            <view class="time-picker">
              <text>{{ openTime || '选择时间' }}</text>
              <text class="muted">打开时间</text>
            </view>
          </picker>
        </view>
        <view class="preset-row">
          <text v-for="preset in presets" :key="preset.label" class="preset" @click="applyPreset(preset.hours)">
            {{ preset.label }}
          </text>
        </view>
        <view class="button" :class="{ disabled: !canCreate || saving }" @click="createLetter">
          {{ saving ? '封存中' : '封存这封信' }}
        </view>
      </view>

      <view class="summary-grid">
        <view class="summary-card glass-card">
          <text class="summary-number">{{ unlockedCount }}</text>
          <text class="muted">封可以打开</text>
        </view>
        <view class="summary-card glass-card">
          <text class="summary-number">{{ lockedCount }}</text>
          <text class="muted">封还在时间里</text>
        </view>
      </view>

      <view class="section-head">
        <text>时间里的信</text>
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
      </view>
      <view class="stack">
        <view v-for="letter in filteredLetters" :key="letter.id" class="card glass-card letter-card" :class="{ locked: !letter.unlocked }" @click="openLetter(letter)">
          <view class="seal" :class="{ open: letter.unlocked }" />
          <view class="letter-main">
            <text class="letter-title">{{ letter.title }}</text>
            <text class="muted">{{ letter.unlocked ? '已经可以打开' : unlockText(letter.open_at) }}</text>
            <text class="tiny">{{ formatTime(letter.open_at) }}</text>
          </view>
          <text class="mode-pill">{{ letter.recipient_user_id ? '给 TA' : '共同读' }}</text>
        </view>
        <view v-if="!filteredLetters.length" class="card glass-card empty">
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

const title = ref('')
const content = ref('')
const openDate = ref('')
const openTime = ref('')
const recipientMode = ref<'partner' | 'both'>('partner')
const activeFilter = ref<'all' | 'unlocked' | 'locked'>('all')
const letters = ref<any[]>([])
const glow = ref(false)
const saving = ref(false)

const presets = [
  { label: '明晚', hours: 24 },
  { label: '一周后', hours: 24 * 7 },
  { label: '一个月后', hours: 24 * 30 }
]

const filters = [
  { key: 'all', label: '全部' },
  { key: 'unlocked', label: '可读' },
  { key: 'locked', label: '锁定' }
] as const

const canCreate = computed(() => title.value.trim() && content.value.trim() && openDate.value && openTime.value)
const unlockedCount = computed(() => letters.value.filter(letter => letter.unlocked).length)
const lockedCount = computed(() => letters.value.filter(letter => !letter.unlocked).length)
const filteredLetters = computed(() => {
  if (activeFilter.value === 'unlocked') return letters.value.filter(letter => letter.unlocked)
  if (activeFilter.value === 'locked') return letters.value.filter(letter => !letter.unlocked)
  return letters.value
})

const emptyTitle = computed(() => {
  if (activeFilter.value === 'unlocked') return '还没有可以打开的信'
  if (activeFilter.value === 'locked') return '没有等待中的信'
  return '还没有未来信'
})

const emptyText = computed(() => {
  if (activeFilter.value === 'unlocked') return '到了约定时间，它会自动出现在这里。'
  if (activeFilter.value === 'locked') return '现在没有被时间封存的信。'
  return '写一封给未来的 TA，留到某个时刻再读。'
})

onShow(load)

async function load() {
  try {
    letters.value = await request('/future-letters')
  } catch (error: any) {
    uni.showToast({
      title: getErrorMessage(error, '未来信加载失败'),
      icon: 'none'
    })
  }
}

async function createLetter() {
  if (!canCreate.value || saving.value) {
    uni.showToast({ title: '标题、内容和时间都要写', icon: 'none' })
    return
  }
  const openAt = toIso()
  if (new Date(openAt).getTime() <= Date.now()) {
    uni.showToast({ title: '请选择未来的时间', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await request('/future-letters', {
      method: 'POST',
      data: {
        title: title.value.trim(),
        content: content.value.trim(),
        openAt,
        recipientMode: recipientMode.value
      }
    })
    title.value = ''
    content.value = ''
    openDate.value = ''
    openTime.value = ''
    recipientMode.value = 'partner'
    glow.value = true
    uni.showToast({ title: '信已经封存', icon: 'none' })
    setTimeout(() => {
      glow.value = false
    }, 560)
    await load()
  } catch (error: any) {
    uni.showToast({ title: getErrorMessage(error, '未来信保存失败'), icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function openLetter(letter: any) {
  try {
    const detail = await request<any>(`/future-letters/${letter.id}`)
    uni.showModal({ title: detail.title, content: detail.content, showCancel: false })
  } catch (error: any) {
    uni.showToast({
      title: getErrorMessage(error, '暂时打不开这封信'),
      icon: 'none'
    })
  }
}

function pickDate(event: any) {
  openDate.value = event.detail.value
}

function pickTime(event: any) {
  openTime.value = event.detail.value
}

function applyPreset(hours: number) {
  const date = new Date(Date.now() + hours * 60 * 60 * 1000)
  openDate.value = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
  openTime.value = `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function toIso() {
  return `${openDate.value}T${openTime.value}:00`
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

function unlockText(value: string) {
  const target = new Date(value).getTime()
  if (!target || Number.isNaN(target)) return '会在约定时间打开'
  const diff = target - Date.now()
  if (diff <= 0) return '已经可以打开'
  const days = Math.ceil(diff / 86400000)
  if (days > 1) return `还有 ${days} 天打开`
  const hours = Math.max(1, Math.ceil(diff / 3600000))
  return `还有 ${hours} 小时打开`
}

function pad(value: number) {
  return String(value).padStart(2, '0')
}
</script>

<style scoped lang="scss">
.intro,
.composer,
.stack,
.summary-card,
.empty {
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
  box-sizing: border-box;
  width: 100%;
  min-height: 220rpx;
  padding: 24rpx;
  border: 1rpx solid rgba(201, 164, 106, 0.24);
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.58);
  color: var(--color-text);
  font-size: 28rpx;
  line-height: 1.6;
}

.recipient-row,
.time-grid,
.summary-grid {
  display: grid;
  gap: 14rpx;
}

.recipient-row,
.time-grid,
.summary-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.recipient-pill,
.time-picker,
.preset,
.filter-pill,
.mode-pill {
  border-radius: 999rpx;
  color: var(--color-muted);
  border: 1rpx solid var(--color-line);
  background: rgba(255, 253, 252, 0.58);
  font-size: 25rpx;
  font-weight: 700;
}

.recipient-pill {
  min-height: 74rpx;
  display: grid;
  place-items: center;
}

.recipient-pill.active,
.filter-pill.active {
  color: var(--color-text);
  border-color: rgba(143, 77, 77, 0.3);
  box-shadow: 0 12rpx 28rpx rgba(143, 77, 77, 0.12);
}

.time-picker {
  min-height: 94rpx;
  display: grid;
  align-content: center;
  gap: 6rpx;
  padding: 0 20rpx;
  border-radius: 18rpx;
}

.preset-row,
.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.preset,
.filter-pill,
.mode-pill {
  padding: 10rpx 16rpx;
}

.button.disabled {
  opacity: 0.48;
}

.summary-card {
  padding: 24rpx;
  border-radius: 20rpx;
}

.summary-number {
  color: var(--color-cocoa);
  font-size: 52rpx;
  font-weight: 800;
  line-height: 1;
}

.section-head {
  display: grid;
  gap: 14rpx;
  margin-top: 12rpx;
  color: var(--color-text);
  font-size: 32rpx;
  font-weight: 700;
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
  flex: 1;
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

.empty {
  text-align: center;
}
</style>

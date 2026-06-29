<template>
  <view class="page romance-bg anniversary-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="hero">
        <text class="eyebrow">时间也偏爱你们</text>
        <text class="title">把重要的日子藏好</text>
        <text class="sub">生日、第一次见面、下次重逢，都可以被温柔地提醒。</text>
      </view>

      <view class="card glass-card composer" :class="{ 'tap-glow': savedGlow }">
        <view class="form-head">
          <text class="form-title">{{ editingId ? '修改这个日子' : '藏一个日期' }}</text>
          <text v-if="editingId" class="cancel-edit" @click="resetForm">取消</text>
        </view>
        <input v-model="title" placeholder="纪念日名称" />
        <picker mode="date" :value="eventDate" @change="pickDate">
          <view class="date-picker">
            <text>{{ eventDate || '选择日期' }}</text>
            <text class="muted">轻点选择</text>
          </view>
        </picker>
        <view class="theme-row">
          <view
            v-for="theme in themes"
            :key="theme.key"
            class="theme-pill"
            :class="[theme.key, { active: cardTheme === theme.key }]"
            @click="cardTheme = theme.key"
          >
            {{ theme.name }}
          </view>
        </view>
        <view class="button" :class="{ disabled: !canSave || saving }" @click="save">
          {{ saving ? '保存中' : editingId ? '保存修改' : '添加纪念日' }}
        </view>
      </view>

      <view v-if="nextAnniversary" class="spotlight glass-card pulse-soft" :class="nextAnniversary.card_theme || 'warm'">
        <text class="spot-label">最近的日子</text>
        <text class="spot-title">{{ nextAnniversary.title }}</text>
        <text class="spot-days">{{ describeDays(nextAnniversary) }}</text>
        <text class="spot-date">{{ nextAnniversary.event_date }}</text>
      </view>

      <view class="section-head">
        <text>我们的日期</text>
        <text class="count">{{ anniversaries.length }} 个</text>
      </view>

      <view class="list">
        <view
          v-for="item in anniversaries"
          :key="item.id"
          class="card glass-card row"
          :class="item.card_theme || 'warm'"
        >
          <view class="date-badge">
            <text>{{ monthDay(item.event_date).month }}</text>
            <text>{{ monthDay(item.event_date).day }}</text>
          </view>
          <view class="row-main">
            <text class="row-title">{{ item.title }}</text>
            <text class="muted">{{ item.event_date }}</text>
            <view class="row-actions">
              <text @click="edit(item)">编辑</text>
              <text class="delete-link" @click="remove(item)">删除</text>
            </view>
          </view>
          <text class="days-tag">{{ describeDays(item) }}</text>
        </view>
        <view v-if="!anniversaries.length" class="card glass-card empty">
          <text>还没有纪念日。</text>
          <text class="muted">先把“在一起的那天”放进来。</text>
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

const title = ref('')
const eventDate = ref('')
const cardTheme = ref('warm')
const editingId = ref<number | null>(null)
const anniversaries = ref<any[]>([])
const saving = ref(false)
const savedGlow = ref(false)

const themes = [
  { key: 'warm', name: '暖光' },
  { key: 'rose', name: '暗玫瑰' },
  { key: 'gold', name: '金色' }
]

const canSave = computed(() => title.value.trim() && eventDate.value)
const nextAnniversary = computed(() => sortedByNext(anniversaries.value)[0])

onShow(load)

async function load() {
  if (!(await ensurePairedSpace())) return
  const list = await request<any[]>('/anniversaries')
  anniversaries.value = sortedByNext(list)
}

function pickDate(event: any) {
  eventDate.value = event.detail.value
}

async function save() {
  if (!canSave.value || saving.value) {
    uni.showToast({ title: '先写名称并选择日期', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const payload = {
      title: title.value.trim(),
      eventDate: eventDate.value,
      eventType: 'custom',
      cardTheme: cardTheme.value
    }
    if (editingId.value) {
      await request(`/anniversaries/${editingId.value}`, { method: 'PUT', data: payload })
    } else {
      await request('/anniversaries', { method: 'POST', data: payload })
    }
    resetForm()
    savedGlow.value = true
    uni.showToast({ title: '日子已收好', icon: 'none' })
    setTimeout(() => {
      savedGlow.value = false
    }, 650)
    await load()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '保存纪念日失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

function edit(item: any) {
  editingId.value = item.id
  title.value = item.title || ''
  eventDate.value = normalizeDate(item.event_date)
  cardTheme.value = item.card_theme || 'warm'
}

function remove(item: any) {
  uni.showModal({
    title: '删除这个日子',
    content: `确认删除“${item.title}”吗？`,
    confirmText: '删除',
    confirmColor: '#9E4D43',
    success: async result => {
      if (!result.confirm) return
      try {
        await request(`/anniversaries/${item.id}`, { method: 'DELETE' })
        if (editingId.value === item.id) resetForm()
        uni.showToast({ title: '已删除', icon: 'none' })
        await load()
      } catch (error: any) {
        uni.showToast({ title: error?.message || '删除失败', icon: 'none' })
      }
    }
  })
}

function resetForm() {
  title.value = ''
  eventDate.value = ''
  cardTheme.value = 'warm'
  editingId.value = null
}

function sortedByNext(list: any[]) {
  return [...(list || [])].sort((a, b) => nextDistance(a.event_date) - nextDistance(b.event_date))
}

function nextDistance(dateText: string) {
  const date = parseDate(dateText)
  if (!date) return Number.MAX_SAFE_INTEGER
  const today = startOfDay(new Date())
  const next = new Date(today.getFullYear(), date.getMonth(), date.getDate())
  if (next.getTime() < today.getTime()) {
    next.setFullYear(next.getFullYear() + 1)
  }
  return Math.ceil((next.getTime() - today.getTime()) / 86400000)
}

function describeDays(item: any) {
  const date = parseDate(item.event_date)
  if (!date) return '待确认'
  const today = startOfDay(new Date())
  const origin = startOfDay(date)
  const passed = Math.floor((today.getTime() - origin.getTime()) / 86400000)
  if (passed === 0) return '就是今天'
  if (passed > 0) {
    const next = nextDistance(item.event_date)
    return next === 0 ? `第 ${passed + 1} 天` : `还有 ${next} 天`
  }
  return `还有 ${Math.abs(passed)} 天`
}

function monthDay(dateText: string) {
  const date = parseDate(dateText)
  if (!date) return { month: '--', day: '--' }
  return {
    month: `${date.getMonth() + 1}月`,
    day: String(date.getDate()).padStart(2, '0')
  }
}

function normalizeDate(dateText: string) {
  return dateText?.includes('T') ? dateText.slice(0, 10) : dateText
}

function parseDate(dateText: string) {
  if (!dateText) return null
  const [year, month, day] = normalizeDate(dateText).split('-').map(Number)
  if (!year || !month || !day) return null
  return new Date(year, month - 1, day)
}

function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}
</script>

<style scoped lang="scss">
.anniversary-page {
  min-height: 100vh;
}

.content-layer,
.list {
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
.count,
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

.composer,
.empty {
  display: grid;
  gap: 20rpx;
}

.form-head,
.date-picker,
.section-head,
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.form-title,
.section-head {
  color: var(--color-text);
  font-size: 30rpx;
  font-weight: 700;
}

.cancel-edit,
.row-actions {
  color: var(--color-accent);
  font-size: 25rpx;
  font-weight: 700;
}

input,
.date-picker {
  box-sizing: border-box;
  width: 100%;
  min-height: 92rpx;
  padding: 0 24rpx;
  border: 1rpx solid rgba(201, 164, 106, 0.24);
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.58);
  color: var(--color-text);
  font-size: 28rpx;
}

.theme-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
}

.theme-pill {
  min-height: 72rpx;
  border-radius: 999rpx;
  display: grid;
  place-items: center;
  color: var(--color-muted);
  border: 1rpx solid rgba(46, 42, 39, 0.08);
  background: rgba(255, 253, 252, 0.5);
  font-size: 25rpx;
  font-weight: 700;
}

.theme-pill.active {
  color: var(--color-text);
  border-color: rgba(201, 164, 106, 0.5);
  box-shadow: 0 14rpx 28rpx rgba(201, 164, 106, 0.16);
}

.button.disabled {
  opacity: 0.48;
}

.spotlight {
  display: grid;
  gap: 8rpx;
  padding: 34rpx;
  overflow: hidden;
}

.spot-label {
  color: var(--color-muted);
  font-size: 24rpx;
}

.spot-title {
  color: var(--color-text);
  font-size: 40rpx;
  font-weight: 700;
}

.spot-days {
  color: var(--color-accent);
  font-size: 58rpx;
  font-weight: 800;
  line-height: 1.2;
}

.spot-date {
  color: var(--color-soft);
  font-size: 26rpx;
}

.section-head {
  padding: 0 4rpx;
}

.row {
  min-height: 146rpx;
}

.date-badge {
  flex: 0 0 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 2rpx;
  color: var(--color-text);
  background: rgba(201, 164, 106, 0.16);
  font-size: 23rpx;
  font-weight: 700;
}

.row-main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 8rpx;
}

.row-title {
  color: var(--color-text);
  font-size: 30rpx;
  font-weight: 700;
}

.row-actions {
  display: flex;
  gap: 28rpx;
}

.delete-link {
  color: #9e4d43;
}

.days-tag {
  flex: 0 0 auto;
  max-width: 178rpx;
  padding: 12rpx 16rpx;
  border-radius: 999rpx;
  color: var(--color-text);
  background: rgba(255, 253, 252, 0.65);
  font-size: 24rpx;
  font-weight: 700;
  text-align: center;
}

.rose {
  background-color: rgba(216, 167, 160, 0.18);
}

.gold {
  background-color: rgba(201, 164, 106, 0.16);
}

.warm {
  background-color: rgba(255, 253, 252, 0.6);
}
</style>

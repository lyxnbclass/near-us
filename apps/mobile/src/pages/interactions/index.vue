<template>
  <view class="page romance-bg interactions-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="intro">
        <text class="romance-kicker">TINY SIGNALS</text>
        <text class="headline">把今天的小心动留给 TA</text>
        <text class="muted">不催促、不打卡，只收集两个人愿意分享的片刻。</text>
      </view>

      <view class="card glass-card topic-card" :class="{ 'tap-glow': topicGlow }">
        <view class="card-head">
          <text class="block-title">今日话题</text>
          <text class="status-pill">{{ topic?.unlocked ? '已解锁' : '等待彼此回答' }}</text>
        </view>
        <text class="question">{{ topic?.topic?.question || '今晚想和对方聊点什么？' }}</text>
        <textarea v-model="topicAnswer" placeholder="先写下你的答案，双方都答后再看见彼此。" />
        <view class="button" @click="submitTopicAnswer">写下答案</view>
        <view v-if="topic?.answers?.length" class="answers">
          <view v-for="answer in topic.answers" :key="answer.user_id" class="answer">
            <text class="answer-name">{{ answer.nickname }}</text>
            <text>{{ answer.answer }}</text>
          </view>
        </view>
      </view>

      <view class="section-title">心意卡片</view>
      <view class="card glass-card composer" :class="{ 'tap-glow': cardGlow }">
        <input class="input" v-model="cardTitle" placeholder="比如：给你点了热奶茶" />
        <input class="input" v-model="cardAmount" placeholder="金额，可选" type="digit" />
        <textarea v-model="cardMessage" placeholder="写一句悄悄话" />
        <view class="button" @click="createCard">生成心意卡片</view>
      </view>
      <view class="stack">
        <view v-for="card in affectionCards" :key="card.id" class="card glass-card affection-card">
          <text class="block-title">{{ card.title }}</text>
          <text v-if="card.amount" class="amount">¥{{ card.amount }}</text>
          <text class="muted">{{ card.message }}</text>
          <text class="tiny">{{ card.nickname }} 留下的心意</text>
        </view>
      </view>

      <view class="section-head">
        <text>共同愿望</text>
        <text class="count">{{ completedWishes }}/{{ wishes.length }} 已完成</text>
      </view>
      <view class="card glass-card composer" :class="{ 'tap-glow': wishGlow }">
        <input class="input" v-model="wishTitle" placeholder="想一起做什么？" />
        <input class="input" v-model="wishNote" placeholder="补充一句，可选" />
        <view class="button" @click="createWish">加进愿望清单</view>
      </view>
      <view class="stack">
        <view v-for="wish in wishes" :key="wish.id" class="card glass-card wish-row" :class="{ done: wish.completed }">
          <view v-if="editingWishId !== wish.id" class="wish-main">
            <text class="block-title">{{ wish.title }}</text>
            <text class="muted">{{ wish.note || '等一个刚刚好的时候' }}</text>
            <view class="wish-meta">
              <text>{{ wish.nickname || '我们' }} 创建</text>
              <text v-if="wish.completed_at">完成于 {{ formatTime(wish.completed_at) }}</text>
            </view>
            <view class="wish-actions">
              <text @click="startEditWish(wish)">编辑</text>
              <text @click="toggleWish(wish)">{{ wish.completed ? '恢复' : '完成' }}</text>
              <text class="delete-link" @click="removeWish(wish)">删除</text>
            </view>
          </view>
          <view v-else class="wish-editor" :class="{ 'tap-glow': wishEditGlow }">
            <input class="input" v-model="editWishTitle" placeholder="愿望标题" />
            <input class="input" v-model="editWishNote" placeholder="补充一句，可选" />
            <view class="editor-actions">
              <view class="ghost-button" @click="cancelEditWish">取消</view>
              <view class="button" :class="{ disabled: !editWishTitle.trim() || editingWish }" @click="saveWish(wish)">
                {{ editingWish ? '保存中' : '保存' }}
              </view>
            </view>
          </view>
        </view>
        <view v-if="!wishes.length" class="card glass-card empty">
          <text>还没有共同愿望。</text>
          <text class="muted">写下一件想一起完成的小事吧。</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '@/api/client'

const topic = ref<any>(null)
const topicAnswer = ref('')
const topicGlow = ref(false)

const affectionCards = ref<any[]>([])
const cardTitle = ref('')
const cardAmount = ref('')
const cardMessage = ref('')
const cardGlow = ref(false)

const wishes = ref<any[]>([])
const wishTitle = ref('')
const wishNote = ref('')
const wishGlow = ref(false)
const editingWishId = ref<number | null>(null)
const editWishTitle = ref('')
const editWishNote = ref('')
const editingWish = ref(false)
const wishEditGlow = ref(false)

const completedWishes = computed(() => wishes.value.filter(item => item.completed).length)

onShow(load)

async function load() {
  topic.value = await request('/interactions/daily-topic')
  affectionCards.value = await request('/interactions/affection-cards')
  wishes.value = await request('/interactions/wishes')
}

async function submitTopicAnswer() {
  if (!topicAnswer.value.trim()) {
    uni.showToast({ title: '先写一句答案', icon: 'none' })
    return
  }
  await request('/interactions/daily-topic/answer', {
    method: 'POST',
    data: { answer: topicAnswer.value }
  })
  topicAnswer.value = ''
  topicGlow.value = true
  uni.showToast({ title: '答案已放进今天', icon: 'none' })
  setTimeout(() => {
    topicGlow.value = false
  }, 560)
  await load()
}

async function createCard() {
  if (!cardTitle.value.trim()) {
    uni.showToast({ title: '写个标题吧', icon: 'none' })
    return
  }
  await request('/interactions/affection-cards', {
    method: 'POST',
    data: {
      title: cardTitle.value,
      amount: cardAmount.value ? Number(cardAmount.value) : null,
      message: cardMessage.value
    }
  })
  cardTitle.value = ''
  cardAmount.value = ''
  cardMessage.value = ''
  cardGlow.value = true
  uni.showToast({ title: '心意已收好', icon: 'none' })
  setTimeout(() => {
    cardGlow.value = false
  }, 560)
  await load()
}

async function createWish() {
  if (!wishTitle.value.trim()) {
    uni.showToast({ title: '写下一个愿望', icon: 'none' })
    return
  }
  await request('/interactions/wishes', {
    method: 'POST',
    data: { title: wishTitle.value, note: wishNote.value }
  })
  wishTitle.value = ''
  wishNote.value = ''
  wishGlow.value = true
  uni.showToast({ title: '愿望已加入', icon: 'none' })
  setTimeout(() => {
    wishGlow.value = false
  }, 560)
  await load()
}

async function completeWish(wish: any) {
  if (wish.completed) return
  await request(`/interactions/wishes/${wish.id}/complete`, { method: 'POST' })
  uni.showToast({ title: '一起完成了一件事', icon: 'none' })
  await load()
}

async function toggleWish(wish: any) {
  if (wish.completed) {
    await request(`/interactions/wishes/${wish.id}/reopen`, { method: 'POST' })
    uni.showToast({ title: '已放回愿望清单', icon: 'none' })
  } else {
    await request(`/interactions/wishes/${wish.id}/complete`, { method: 'POST' })
    uni.showToast({ title: '一起完成了一件事', icon: 'none' })
  }
  await load()
}

function startEditWish(wish: any) {
  editingWishId.value = wish.id
  editWishTitle.value = wish.title || ''
  editWishNote.value = wish.note || ''
}

function cancelEditWish() {
  editingWishId.value = null
  editWishTitle.value = ''
  editWishNote.value = ''
}

async function saveWish(wish: any) {
  if (!editWishTitle.value.trim() || editingWish.value) {
    uni.showToast({ title: '愿望标题不能为空', icon: 'none' })
    return
  }
  editingWish.value = true
  try {
    await request(`/interactions/wishes/${wish.id}`, {
      method: 'PUT',
      data: { title: editWishTitle.value.trim(), note: editWishNote.value.trim() }
    })
    wishEditGlow.value = true
    uni.showToast({ title: '愿望已更新', icon: 'none' })
    setTimeout(() => {
      wishEditGlow.value = false
    }, 650)
    cancelEditWish()
    await load()
  } finally {
    editingWish.value = false
  }
}

function removeWish(wish: any) {
  uni.showModal({
    title: '删除这个愿望',
    content: `确认删除“${wish.title}”吗？`,
    confirmText: '删除',
    confirmColor: '#9E4D43',
    success: async result => {
      if (!result.confirm) return
      await request(`/interactions/wishes/${wish.id}`, { method: 'DELETE' })
      if (editingWishId.value === wish.id) cancelEditWish()
      uni.showToast({ title: '已删除', icon: 'none' })
      await load()
    }
  })
}

function formatTime(value?: string) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}
</script>

<style scoped lang="scss">
.intro {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin: 28rpx 0 24rpx;
}

.headline {
  font-size: 44rpx;
  font-weight: 600;
  line-height: 1.25;
  color: var(--color-text);
}

.topic-card,
.composer,
.stack {
  display: grid;
  gap: 18rpx;
}

.card-head,
.section-head,
.wish-row,
.editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.block-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-cocoa);
}

.status-pill,
.complete-button {
  flex: 0 0 auto;
  border-radius: 999rpx;
  padding: 12rpx 18rpx;
  color: var(--color-rose);
  background: rgba(217, 167, 160, 0.16);
  font-size: 24rpx;
}

.count {
  color: var(--color-muted);
  font-size: 24rpx;
  font-weight: 600;
}

.question {
  font-size: 34rpx;
  font-weight: 600;
  line-height: 1.45;
}

textarea {
  min-height: 170rpx;
  width: 100%;
  line-height: 1.6;
}

.answers,
.answer {
  display: grid;
  gap: 12rpx;
}

.answer {
  padding: 18rpx;
  border-radius: 18rpx;
  background: rgba(255, 253, 250, 0.54);
}

.answer-name,
.tiny {
  color: var(--color-rose);
  font-size: 24rpx;
  font-weight: 600;
}

.affection-card {
  display: grid;
  gap: 12rpx;
}

.amount {
  font-size: 42rpx;
  font-weight: 600;
  color: var(--color-rose);
}

.wish-row {
  align-items: stretch;
}

.wish-main,
.wish-editor,
.empty {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 12rpx;
}

.wish-row.done {
  opacity: 0.72;
}

.wish-meta,
.wish-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  color: var(--color-muted);
  font-size: 23rpx;
}

.wish-actions {
  color: var(--color-accent);
  font-weight: 700;
}

.delete-link {
  color: #9e4d43;
}

.editor-actions {
  align-items: stretch;
}

.ghost-button,
.editor-actions .button {
  flex: 1;
}

.ghost-button {
  min-height: 88rpx;
  border-radius: 22rpx;
  display: grid;
  place-items: center;
  color: var(--color-text);
  border: 1rpx solid var(--color-line);
  background: rgba(255, 253, 252, 0.58);
  font-weight: 700;
}

.button.disabled {
  opacity: 0.48;
}
</style>

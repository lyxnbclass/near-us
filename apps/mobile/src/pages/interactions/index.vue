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
        <view class="button" :class="{ disabled: !topicAnswer.trim() || topicSaving }" @click="submitTopicAnswer">
          {{ topicSaving ? '保存中' : '写下答案' }}
        </view>
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
        <view class="button" :class="{ disabled: !cardTitle.trim() || cardSaving }" @click="createCard">
          {{ cardSaving ? '生成中' : '生成心意卡片' }}
        </view>
      </view>
      <view class="stack">
        <view v-for="card in affectionCards" :key="card.id" class="card glass-card affection-card">
          <text class="block-title">{{ card.title }}</text>
          <text v-if="card.amount" class="amount">¥{{ card.amount }}</text>
          <text class="muted">{{ card.message }}</text>
          <text class="tiny">{{ card.nickname }} 留下的心意</text>
        </view>
      </view>

      <view class="section-title">共同愿望</view>
      <view class="card glass-card composer" :class="{ 'tap-glow': wishGlow }">
        <input class="input" v-model="wishTitle" placeholder="想一起做什么？" />
        <input class="input" v-model="wishNote" placeholder="补充一句，可选" />
        <view class="button" :class="{ disabled: !wishTitle.trim() || wishSaving }" @click="createWish">
          {{ wishSaving ? '加入中' : '加进愿望清单' }}
        </view>
      </view>
      <view class="stack">
        <view v-for="wish in wishes" :key="wish.id" class="card glass-card wish-row" :class="{ done: wish.completed }">
          <view>
            <text class="block-title">{{ wish.title }}</text>
            <text class="muted">{{ wish.note || '等一个刚刚好的时候' }}</text>
          </view>
          <view class="complete-button" :class="{ disabled: wishCompletingId === wish.id }" @click="completeWish(wish)">
            {{ wish.completed ? '已完成' : wishCompletingId === wish.id ? '完成中' : '完成' }}
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
import { ensurePairedSpace } from '@/utils/spaceGuard'

const topic = ref<any>(null)
const topicAnswer = ref('')
const topicGlow = ref(false)
const topicSaving = ref(false)

const affectionCards = ref<any[]>([])
const cardTitle = ref('')
const cardAmount = ref('')
const cardMessage = ref('')
const cardGlow = ref(false)
const cardSaving = ref(false)

const wishes = ref<any[]>([])
const wishTitle = ref('')
const wishNote = ref('')
const wishGlow = ref(false)
const wishSaving = ref(false)
const wishCompletingId = ref<number | null>(null)

onShow(load)

async function load() {
  if (!(await ensurePairedSpace())) return
  topic.value = await request('/interactions/daily-topic')
  affectionCards.value = await request('/interactions/affection-cards')
  wishes.value = await request('/interactions/wishes')
}

async function submitTopicAnswer() {
  if (!topicAnswer.value.trim() || topicSaving.value) {
    uni.showToast({ title: '先写一句答案', icon: 'none' })
    return
  }
  topicSaving.value = true
  try {
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
  } catch (error: any) {
    uni.showToast({ title: error?.message || '保存答案失败', icon: 'none' })
  } finally {
    topicSaving.value = false
  }
}

async function createCard() {
  if (!cardTitle.value.trim() || cardSaving.value) {
    uni.showToast({ title: '写个标题吧', icon: 'none' })
    return
  }
  cardSaving.value = true
  try {
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
  } catch (error: any) {
    uni.showToast({ title: error?.message || '保存心意卡片失败', icon: 'none' })
  } finally {
    cardSaving.value = false
  }
}

async function createWish() {
  if (!wishTitle.value.trim() || wishSaving.value) {
    uni.showToast({ title: '写下一个愿望', icon: 'none' })
    return
  }
  wishSaving.value = true
  try {
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
  } catch (error: any) {
    uni.showToast({ title: error?.message || '保存愿望失败', icon: 'none' })
  } finally {
    wishSaving.value = false
  }
}

async function completeWish(wish: any) {
  if (wish.completed || wishCompletingId.value) return
  wishCompletingId.value = wish.id
  try {
    await request(`/interactions/wishes/${wish.id}/complete`, { method: 'POST' })
    uni.showToast({ title: '一起完成了一件事', icon: 'none' })
    await load()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '完成愿望失败', icon: 'none' })
  } finally {
    wishCompletingId.value = null
  }
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
.wish-row {
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

.button.disabled,
.complete-button.disabled {
  opacity: 0.48;
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

.wish-row.done {
  opacity: 0.72;
}
</style>

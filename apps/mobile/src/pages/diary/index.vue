<template>
  <view class="page romance-bg diary-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view v-if="!currentDiary" class="list-view">
        <view class="hero">
          <text class="eyebrow">心事可以慢慢写</text>
          <text class="title">今天想留下什么？</text>
          <text class="sub">私密日记只属于你，共享日记才会被 TA 看见和回应。</text>
        </view>

        <view class="card glass-card composer" :class="{ 'tap-glow': savedGlow }">
          <input v-model="title" placeholder="给这页日记起个名字" />
          <textarea v-model="content" maxlength="600" placeholder="写下今天想保存的片段" />
          <view class="segmented">
            <view
              class="segment"
              :class="{ active: visibility === 'private' }"
              @click="visibility = 'private'"
            >
              只给自己
            </view>
            <view
              class="segment"
              :class="{ active: visibility === 'shared' }"
              @click="visibility = 'shared'"
            >
              和 TA 共享
            </view>
          </view>
          <view class="button" :class="{ disabled: !canSave || saving }" @click="create">
            {{ saving ? '保存中' : '保存日记' }}
          </view>
        </view>

        <view class="section-head">
          <text>日记</text>
          <text class="count">{{ diaries.length }} 页</text>
        </view>
        <view class="list">
          <view v-for="item in diaries" :key="item.id" class="card glass-card item" @click="open(item.id)">
            <view>
              <text class="item-title">{{ item.title || '未命名的一页' }}</text>
              <text class="muted">{{ formatTime(item.created_at) }}</text>
            </view>
            <text class="badge" :class="item.visibility">{{ item.visibility === 'private' ? '私密' : '共享' }}</text>
          </view>
          <view v-if="!diaries.length" class="card glass-card empty">
            <text>还没有日记。</text>
            <text class="muted">可以先写一页只给自己看的。</text>
          </view>
        </view>
      </view>

      <view v-else class="detail-view">
        <view class="detail-head">
          <view class="ghost-button small" @click="closeDetail">返回</view>
          <text class="badge" :class="currentDiary.visibility">
            {{ currentDiary.visibility === 'private' ? '私密' : '共享' }}
          </text>
        </view>

        <view class="card glass-card diary-detail">
          <text class="detail-title">{{ currentDiary.title }}</text>
          <text class="muted">{{ formatTime(currentDiary.created_at) }}</text>
          <text class="diary-content">{{ currentDiary.content }}</text>
        </view>

        <view v-if="currentDiary.visibility === 'shared'" class="comments">
          <view class="section-head">
            <text>留言</text>
            <text class="count">{{ comments.length }} 条</text>
          </view>
          <view v-for="comment in comments" :key="comment.id" class="card glass-card comment">
            <text class="comment-name">{{ comment.nickname || '我们' }}</text>
            <text class="comment-content">{{ comment.content }}</text>
            <text class="muted">{{ formatTime(comment.created_at) }}</text>
          </view>
          <view v-if="!comments.length" class="card glass-card empty">
            <text>还没有留言。</text>
            <text class="muted">可以轻轻回应这一页。</text>
          </view>
          <view class="card glass-card comment-box" :class="{ 'tap-glow': commentGlow }">
            <textarea v-model="commentText" maxlength="160" placeholder="给这页日记留一句话" />
            <view class="button" :class="{ disabled: !commentText.trim() || commenting }" @click="sendComment">
              {{ commenting ? '发送中' : '留下回应' }}
            </view>
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
import { ensurePairedSpace } from '@/utils/spaceGuard'

const title = ref('')
const content = ref('')
const visibility = ref<'private' | 'shared'>('private')
const diaries = ref<any[]>([])
const currentDiary = ref<any | null>(null)
const comments = ref<any[]>([])
const commentText = ref('')
const saving = ref(false)
const commenting = ref(false)
const savedGlow = ref(false)
const commentGlow = ref(false)

const canSave = computed(() => title.value.trim() && content.value.trim())

onShow(load)

async function load() {
  if (!(await ensurePairedSpace())) return
  diaries.value = await request('/diaries')
}

async function create() {
  if (!canSave.value || saving.value) {
    uni.showToast({ title: '先写下标题和内容', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await request('/diaries', {
      method: 'POST',
      data: {
        title: title.value.trim(),
        content: content.value.trim(),
        visibility: visibility.value
      }
    })
    title.value = ''
    content.value = ''
    visibility.value = 'private'
    savedGlow.value = true
    uni.showToast({ title: '日记已收好', icon: 'none' })
    setTimeout(() => {
      savedGlow.value = false
    }, 650)
    await load()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '保存日记失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

async function open(id: number) {
  try {
    currentDiary.value = await request<any>(`/diaries/${id}`)
    comments.value = currentDiary.value?.comments || []
    if (currentDiary.value?.visibility === 'shared' && !comments.value.length) {
      comments.value = await request<any[]>(`/diaries/${id}/comments`)
    }
  } catch (error: any) {
    uni.showToast({ title: error?.message || '打开日记失败', icon: 'none' })
  }
}

function closeDetail() {
  currentDiary.value = null
  comments.value = []
  commentText.value = ''
}

async function sendComment() {
  if (!currentDiary.value || !commentText.value.trim() || commenting.value) {
    return
  }
  commenting.value = true
  try {
    await request(`/diaries/${currentDiary.value.id}/comments`, {
      method: 'POST',
      data: { content: commentText.value.trim() }
    })
    commentText.value = ''
    comments.value = await request<any[]>(`/diaries/${currentDiary.value.id}/comments`)
    commentGlow.value = true
    uni.showToast({ title: '回应已留下', icon: 'none' })
    setTimeout(() => {
      commentGlow.value = false
    }, 650)
  } catch (error: any) {
    uni.showToast({ title: error?.message || '发送留言失败', icon: 'none' })
  } finally {
    commenting.value = false
  }
}

function formatTime(value?: string) {
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped lang="scss">
.diary-page {
  min-height: 100vh;
}

.content-layer,
.list-view,
.detail-view,
.list,
.comments {
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
.diary-detail,
.comment-box,
.empty {
  display: grid;
  gap: 20rpx;
}

input,
textarea {
  box-sizing: border-box;
  width: 100%;
  padding: 24rpx;
  border: 1rpx solid rgba(201, 164, 106, 0.24);
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.58);
  color: var(--color-text);
  font-size: 28rpx;
}

textarea {
  min-height: 220rpx;
  line-height: 1.55;
}

.segmented {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10rpx;
  padding: 8rpx;
  border-radius: 18rpx;
  background: rgba(46, 42, 39, 0.06);
}

.segment {
  min-height: 76rpx;
  display: grid;
  place-items: center;
  border-radius: 14rpx;
  color: var(--color-muted);
  font-size: 26rpx;
  font-weight: 700;
}

.segment.active {
  background: rgba(255, 253, 252, 0.86);
  color: var(--color-text);
  box-shadow: 0 14rpx 28rpx rgba(46, 42, 39, 0.08);
}

.button.disabled {
  opacity: 0.48;
}

.section-head,
.detail-head,
.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.section-head {
  padding: 0 4rpx;
  color: var(--color-text);
  font-size: 30rpx;
  font-weight: 700;
}

.item {
  min-height: 112rpx;
}

.item-title {
  display: block;
  color: var(--color-text);
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 8rpx;
}

.badge {
  flex: 0 0 auto;
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  font-size: 23rpx;
  color: var(--color-text);
  background: rgba(201, 164, 106, 0.18);
}

.badge.private {
  background: rgba(46, 42, 39, 0.08);
}

.badge.shared {
  background: rgba(216, 167, 160, 0.24);
}

.ghost-button.small {
  width: 120rpx;
  height: 68rpx;
  border-radius: 999rpx;
  display: grid;
  place-items: center;
  color: var(--color-text);
  background: rgba(255, 253, 252, 0.66);
  border: 1rpx solid rgba(46, 42, 39, 0.1);
  font-weight: 700;
}

.detail-title {
  color: var(--color-text);
  font-size: 42rpx;
  font-weight: 700;
  line-height: 1.25;
}

.diary-content {
  color: var(--color-text);
  font-size: 30rpx;
  line-height: 1.9;
  white-space: pre-wrap;
}

.comment {
  display: grid;
  gap: 10rpx;
}

.comment-name {
  color: var(--color-accent);
  font-size: 25rpx;
  font-weight: 700;
}

.comment-content {
  color: var(--color-text);
  font-size: 28rpx;
  line-height: 1.6;
}
</style>

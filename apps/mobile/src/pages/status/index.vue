<template>
  <view class="page romance-bg status-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="intro">
        <text class="romance-kicker">A SMALL SIGNAL</text>
        <text class="headline">让 TA 知道你此刻在想什么</text>
        <text class="muted">一张照片，一句近况。想分享的时候点一下就好。</text>
      </view>

      <view class="card glass-card composer" :class="{ 'tap-glow': publishedGlow }">
        <textarea v-model="content" maxlength="240" placeholder="此刻想让 TA 知道什么？" />
        <view class="tags">
          <text v-for="tag in tags" :key="tag" class="tag" :class="{ active: moodTag === tag }" @click="moodTag = tag">
            {{ tag }}
          </text>
        </view>

        <view class="photo-picker" @click="choosePhoto">
          <image v-if="selectedPhoto" class="photo-preview" :src="selectedPhoto" mode="aspectFill" />
          <view v-else class="photo-empty">
            <text>加一张此刻的照片</text>
            <text class="muted">可选，不发也很好</text>
          </view>
        </view>
        <view v-if="selectedPhoto" class="photo-actions">
          <text @click="choosePhoto">换一张</text>
          <text class="delete-link" @click.stop="clearPhoto">移除</text>
        </view>

        <view class="button" :class="{ disabled: !canPublish || publishing }" @click="publish">
          {{ publishing ? '送达中' : '轻轻发给 TA' }}
        </view>
      </view>

      <view class="section-head">
        <text>最近的靠近</text>
        <text class="count">{{ statuses.length }} 条</text>
      </view>
      <view class="timeline">
        <view v-for="item in statuses" :key="item.id" class="card glass-card item">
          <image v-if="statusImage(item)" class="status-photo" :src="statusImage(item)" mode="aspectFill" />
          <text class="status-text">{{ item.content || '轻轻发来一个此刻。' }}</text>
          <text class="muted">{{ item.nickname || '我们' }} · {{ item.mood_tag || '在想你' }}</text>
          <view v-if="reactionList(item).length" class="reaction-list">
            <text v-for="(reaction, index) in reactionList(item)" :key="index" class="reaction-pill">{{ reaction }}</text>
          </view>
          <view class="reaction-actions">
            <text v-for="reaction in reactions" :key="reaction" class="reaction-action" @click="react(item, reaction)">
              {{ reaction }}
            </text>
          </view>
        </view>
        <view v-if="!statuses.length" class="card glass-card empty">
          <text>还没有动态。</text>
          <text class="muted">先发一个很轻的此刻。</text>
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
import { isUserCancel } from '@/utils/uniErrors'

const tags = ['在想你', '想被抱一下', '在加班', '刚吃饭', '睡前想你']
const reactions = ['抱抱', '收到啦', '想你']
const content = ref('')
const moodTag = ref('在想你')
const selectedPhoto = ref('')
const selectedName = ref('')
const selectedSize = ref(0)
const statuses = ref<any[]>([])
const publishedGlow = ref(false)
const publishing = ref(false)

const canPublish = computed(() => content.value.trim() || selectedPhoto.value)

onShow(load)

async function load() {
  if (!(await ensurePairedSpace())) return
  statuses.value = await request('/statuses')
}

async function choosePhoto() {
  try {
    const result = await uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera']
    })
    const file = result.tempFiles?.[0] as any
    selectedPhoto.value = result.tempFilePaths?.[0] || file?.path || ''
    selectedName.value = file?.name || `status-${Date.now()}.jpg`
    selectedSize.value = Number(file?.size || 0)
  } catch (error: any) {
    if (!isUserCancel(error)) {
      uni.showToast({ title: error?.message || '选择照片失败', icon: 'none' })
    }
  }
}

function clearPhoto() {
  selectedPhoto.value = ''
  selectedName.value = ''
  selectedSize.value = 0
}

function reactionList(item: any) {
  if (!item.reactions) return []
  return String(item.reactions).split(',').filter(Boolean)
}

function statusImage(item: any) {
  return item.local_url || item.localUrl || item.object_key || ''
}

async function react(item: any, reactionKey: string) {
  try {
    await request(`/statuses/${item.id}/reactions`, {
      method: 'POST',
      data: { reactionKey }
    })
    uni.showToast({ title: `已回应：${reactionKey}`, icon: 'none' })
    await load()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '回应失败', icon: 'none' })
  }
}

async function publish() {
  if (!canPublish.value || publishing.value) {
    uni.showToast({ title: '写一句话或选一张照片', icon: 'none' })
    return
  }
  publishing.value = true
  try {
    let fileId: number | null = null
    if (selectedPhoto.value) {
      const file = await request<{ id: number }>('/files', {
        method: 'POST',
        data: {
          objectKey: selectedPhoto.value,
          originalName: selectedName.value || selectedPhoto.value,
          mimeType: inferMimeType(selectedName.value || selectedPhoto.value),
          sizeBytes: selectedSize.value
        }
      })
      fileId = file.id
    }
    await request('/statuses', {
      method: 'POST',
      data: {
        content: content.value.trim(),
        moodTag: moodTag.value,
        fileId,
        localUrl: selectedPhoto.value
      }
    })
    publishedGlow.value = true
    content.value = ''
    clearPhoto()
    uni.showToast({ title: '已经悄悄送达', icon: 'none' })
    setTimeout(() => {
      publishedGlow.value = false
    }, 560)
    await load()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '发布失败', icon: 'none' })
  } finally {
    publishing.value = false
  }
}

function inferMimeType(name: string) {
  const lower = name.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  if (lower.endsWith('.gif')) return 'image/gif'
  return 'image/jpeg'
}
</script>

<style scoped lang="scss">
.status-page {
  min-height: 100vh;
}

.content-layer {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 24rpx;
}

.intro {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin: 28rpx 0 6rpx;
}

.headline {
  font-size: 44rpx;
  font-weight: 700;
  line-height: 1.25;
  color: var(--color-text);
}

.count,
.muted {
  color: var(--color-muted);
}

.composer,
.timeline,
.item,
.empty {
  display: grid;
  gap: 18rpx;
}

textarea {
  box-sizing: border-box;
  min-height: 210rpx;
  width: 100%;
  padding: 24rpx;
  border: 1rpx solid rgba(201, 164, 106, 0.24);
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.58);
  color: var(--color-text);
  line-height: 1.6;
  font-size: 28rpx;
}

.tags,
.reaction-list,
.reaction-actions,
.photo-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tag {
  padding: 14rpx 20rpx;
  border: 1rpx solid var(--color-line);
  border-radius: 999rpx;
  color: var(--color-muted);
  background: rgba(255, 253, 250, 0.58);
  transition: transform 180ms ease, border-color 180ms ease, color 180ms ease;
}

.tag.active {
  color: var(--color-rose);
  border-color: rgba(143, 77, 77, 0.35);
  box-shadow: 0 0 28rpx rgba(217, 167, 160, 0.22);
  animation: tagPulse 520ms ease-out;
}

.photo-picker {
  min-height: 260rpx;
  border-radius: 20rpx;
  overflow: hidden;
  border: 1rpx dashed rgba(201, 164, 106, 0.42);
  background: rgba(255, 253, 252, 0.5);
}

.photo-preview,
.status-photo {
  display: block;
  width: 100%;
  height: 100%;
}

.photo-empty {
  min-height: 260rpx;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8rpx;
  color: var(--color-text);
}

.photo-actions {
  color: var(--color-accent);
  font-size: 25rpx;
  font-weight: 700;
}

.delete-link {
  color: #9e4d43;
}

.button.disabled {
  opacity: 0.48;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4rpx;
  color: var(--color-text);
  font-size: 30rpx;
  font-weight: 700;
}

.status-photo {
  height: 360rpx;
  border-radius: 18rpx;
}

.reaction-pill,
.reaction-action {
  border-radius: 999rpx;
  padding: 10rpx 16rpx;
  font-size: 24rpx;
}

.reaction-pill {
  color: var(--color-rose);
  background: rgba(217, 167, 160, 0.16);
}

.reaction-action {
  color: var(--color-muted);
  border: 1rpx solid var(--color-line);
  background: rgba(255, 253, 250, 0.54);
}

.status-text {
  line-height: 1.55;
  font-size: 30rpx;
  color: var(--color-text);
}

@keyframes tagPulse {
  0% { transform: scale(0.98); }
  50% { transform: scale(1.04); }
  100% { transform: scale(1); }
}
</style>

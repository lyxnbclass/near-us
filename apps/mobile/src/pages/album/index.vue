<template>
  <view class="page romance-bg album-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="hero">
        <text class="eyebrow">只给我们看的回忆</text>
        <text class="title">把这一刻藏进相册</text>
        <text class="sub">照片、文字和当天的心情，都会慢慢长成你们的小宇宙。</text>
      </view>

      <view class="card glass-card composer" :class="{ 'tap-glow': savedGlow }">
        <view class="photo-picker" @click="choosePhoto">
          <image v-if="selectedPhoto" class="preview" :src="selectedPhoto" mode="aspectFill" />
          <view v-else class="picker-empty">
            <text class="plus">+</text>
            <text>选择一张照片</text>
            <text class="hint">相册或拍照都可以</text>
          </view>
        </view>
        <textarea
          v-model="caption"
          maxlength="80"
          placeholder="给这张回忆留一句悄悄话"
        />
        <view class="actions">
          <view class="ghost-button" @click="choosePhoto">{{ selectedPhoto ? '换一张' : '选照片' }}</view>
          <view class="button" :class="{ disabled: !selectedPhoto || saving }" @click="create">
            {{ saving ? '保存中' : '保存回忆' }}
          </view>
        </view>
      </view>

      <view class="section-head">
        <text>时间线</text>
        <text class="count">{{ albums.length }} 个瞬间</text>
      </view>

      <view class="timeline">
        <view v-for="item in albums" :key="item.id" class="card glass-card item">
          <image v-if="getImageUrl(item)" class="photo" :src="getImageUrl(item)" mode="aspectFill" />
          <view v-else class="photo-placeholder">照片正在生成预览</view>
          <view class="item-body">
            <view v-if="editingId !== item.id" class="caption-view">
              <text class="caption">{{ item.caption || '那一刻，我们都在。' }}</text>
              <text class="muted">{{ albumTimeText(item) }}</text>
              <view class="item-actions">
                <text @click="startEdit(item)">编辑文案</text>
                <text class="delete-link" @click="remove(item)">删除</text>
              </view>
            </view>
            <view v-else class="edit-box" :class="{ 'tap-glow': editGlow }">
              <textarea v-model="editCaption" maxlength="80" placeholder="给这张回忆换一句话" />
              <view class="edit-actions">
                <view class="ghost-button" @click="cancelEdit">取消</view>
                <view class="button" :class="{ disabled: editing }" @click="saveEdit(item)">
                  {{ editing ? '保存中' : '保存文案' }}
                </view>
              </view>
            </view>
          </view>
        </view>
        <view v-if="!albums.length" class="card glass-card empty">
          <text>还没有回忆。</text>
          <text class="muted">选一张照片，让这里先亮起来。</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '@/api/client'

const selectedPhoto = ref('')
const selectedName = ref('')
const selectedSize = ref(0)
const caption = ref('')
const albums = ref<any[]>([])
const saving = ref(false)
const editingId = ref<number | null>(null)
const editCaption = ref('')
const editing = ref(false)
const savedGlow = ref(false)
const editGlow = ref(false)

onShow(load)

async function load() {
  albums.value = await request('/albums')
}

async function choosePhoto() {
  const result = await uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera']
  })
  const file = result.tempFiles?.[0] as any
  selectedPhoto.value = result.tempFilePaths?.[0] || file?.path || ''
  selectedName.value = file?.name || `memory-${Date.now()}.jpg`
  selectedSize.value = Number(file?.size || 0)
}

async function create() {
  if (!selectedPhoto.value || saving.value) {
    uni.showToast({ title: '先选一张照片', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const file = await request<{ id: number }>('/files', {
      method: 'POST',
      data: {
        objectKey: selectedPhoto.value,
        originalName: selectedName.value || selectedPhoto.value,
        mimeType: inferMimeType(selectedName.value || selectedPhoto.value),
        sizeBytes: selectedSize.value
      }
    })
    await request('/albums', {
      method: 'POST',
      data: {
        fileId: file.id,
        caption: caption.value,
        sceneType: 'couple_album',
        localUrl: selectedPhoto.value
      }
    })
    selectedPhoto.value = ''
    selectedName.value = ''
    selectedSize.value = 0
    caption.value = ''
    savedGlow.value = true
    uni.showToast({ title: '回忆已收好', icon: 'none' })
    setTimeout(() => {
      savedGlow.value = false
    }, 650)
    await load()
  } finally {
    saving.value = false
  }
}

function startEdit(item: any) {
  editingId.value = item.id
  editCaption.value = item.caption || ''
}

function cancelEdit() {
  editingId.value = null
  editCaption.value = ''
}

async function saveEdit(item: any) {
  if (editing.value) return
  editing.value = true
  try {
    await request(`/albums/${item.id}`, {
      method: 'PUT',
      data: { caption: editCaption.value.trim() }
    })
    editGlow.value = true
    uni.showToast({ title: '文案已更新', icon: 'none' })
    setTimeout(() => {
      editGlow.value = false
    }, 650)
    cancelEdit()
    await load()
  } finally {
    editing.value = false
  }
}

function remove(item: any) {
  uni.showModal({
    title: '删除这段回忆',
    content: `确认删除“${item.caption || '这一刻'}”吗？`,
    confirmText: '删除',
    confirmColor: '#9E4D43',
    success: async result => {
      if (!result.confirm) return
      await request(`/albums/${item.id}`, { method: 'DELETE' })
      if (editingId.value === item.id) cancelEdit()
      uni.showToast({ title: '已删除', icon: 'none' })
      await load()
    }
  })
}

function getImageUrl(item: any) {
  return item.local_url || item.localUrl || item.object_key || ''
}

function inferMimeType(name: string) {
  const lower = name.toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  if (lower.endsWith('.gif')) return 'image/gif'
  return 'image/jpeg'
}

function formatTime(value?: string) {
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function albumTimeText(item: any) {
  if (item.updated_at && item.updated_at !== item.created_at) {
    return `更新于 ${formatTime(item.updated_at)}`
  }
  return formatTime(item.created_at || item.taken_at)
}
</script>

<style scoped lang="scss">
.album-page {
  min-height: 100vh;
}

.content-layer {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 28rpx;
}

.hero {
  display: grid;
  gap: 12rpx;
  padding: 28rpx 6rpx 4rpx;
}

.eyebrow,
.count,
.muted,
.hint {
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

.composer {
  display: grid;
  gap: 22rpx;
}

.photo-picker {
  min-height: 360rpx;
  border: 1rpx dashed rgba(201, 164, 106, 0.48);
  border-radius: 22rpx;
  overflow: hidden;
  background: rgba(255, 253, 252, 0.52);
}

.preview,
.photo {
  display: block;
  width: 100%;
  height: 100%;
}

.picker-empty {
  min-height: 360rpx;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10rpx;
  color: var(--color-text);
}

.plus {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(46, 42, 39, 0.08);
  color: var(--color-accent);
  font-size: 52rpx;
  line-height: 1;
}

textarea {
  min-height: 132rpx;
  box-sizing: border-box;
  width: 100%;
  padding: 24rpx;
  border: 1rpx solid rgba(201, 164, 106, 0.24);
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.58);
  color: var(--color-text);
  font-size: 28rpx;
  line-height: 1.5;
}

.actions {
  display: grid;
  grid-template-columns: 0.8fr 1.2fr;
  gap: 18rpx;
}

.ghost-button,
.button {
  min-height: 92rpx;
  border-radius: 20rpx;
  display: grid;
  place-items: center;
  font-weight: 700;
}

.ghost-button {
  border: 1rpx solid rgba(46, 42, 39, 0.12);
  color: var(--color-text);
  background: rgba(255, 253, 252, 0.54);
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

.timeline {
  display: grid;
  gap: 22rpx;
}

.item {
  padding: 0;
  overflow: hidden;
}

.photo {
  height: 420rpx;
}

.photo-placeholder {
  height: 360rpx;
  display: grid;
  place-items: center;
  color: var(--color-muted);
  background: linear-gradient(135deg, rgba(239, 228, 217, 0.9), rgba(255, 253, 252, 0.88));
}

.item-body,
.caption-view,
.edit-box,
.empty {
  display: grid;
  gap: 10rpx;
  padding: 24rpx;
}

.caption {
  color: var(--color-text);
  font-size: 30rpx;
  line-height: 1.55;
}

.item-actions,
.edit-actions {
  display: flex;
  align-items: center;
  gap: 24rpx;
  color: var(--color-accent);
  font-size: 25rpx;
  font-weight: 700;
}

.delete-link {
  color: #9e4d43;
}

.edit-actions {
  align-items: stretch;
}

.edit-actions .ghost-button,
.edit-actions .button {
  flex: 1;
}
</style>

<template>
  <view class="page romance-bg pet-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="hero">
        <text class="eyebrow">共同照顾的小成员</text>
        <text class="title">宠物栏</text>
        <text class="sub">记录喂食、散步、撒娇和那些让你们同时心软的小瞬间。</text>
      </view>

      <view class="card glass-card composer" :class="{ 'tap-glow': profileGlow }">
        <view class="form-head">
          <text class="form-title">{{ editingPetId ? '编辑宠物档案' : '宠物档案' }}</text>
          <text v-if="!editingPetId" class="muted">{{ pets.length }} 位小成员</text>
          <text v-else class="action-link" @click="resetProfileForm">取消</text>
        </view>
        <input v-model="name" placeholder="宠物名字" />
        <input v-model="breed" placeholder="品种，可不填" />
        <picker mode="date" :value="birthday" @change="pickBirthday">
          <view class="date-picker">
            <text>{{ birthday || '生日，可不选' }}</text>
            <text class="muted">轻点选择</text>
          </view>
        </picker>
        <view class="button" :class="{ disabled: !name.trim() || savingProfile }" @click="saveProfile">
          {{ savingProfile ? '保存中' : editingPetId ? '保存修改' : '添加宠物' }}
        </view>
      </view>

      <view class="pet-list">
        <view
          v-for="pet in pets"
          :key="pet.id"
          class="card glass-card pet-card"
          :class="{ active: selectedPetId === pet.id }"
          @click="selectPet(pet.id)"
        >
          <view class="avatar">{{ pet.name?.slice(0, 1) || '宠' }}</view>
          <view class="pet-main">
            <text class="pet-name">{{ pet.name }}</text>
            <text class="muted">{{ pet.breed || '共同照顾的小成员' }}</text>
            <view class="pet-actions">
              <text @click.stop="editProfile(pet)">编辑</text>
              <text class="delete-link" @click.stop="removeProfile(pet)">删除</text>
            </view>
          </view>
          <text class="select-mark">{{ selectedPetId === pet.id ? '记录中' : '选择' }}</text>
        </view>
      </view>

      <view class="card glass-card composer" :class="{ 'tap-glow': eventGlow }">
        <view class="form-head">
          <text class="form-title">记录动态</text>
          <text class="muted">{{ selectedPetName || '先选一位小成员' }}</text>
        </view>
        <view class="quick-types">
          <text
            v-for="type in eventTypes"
            :key="type"
            class="type-pill"
            :class="{ active: eventType === type }"
            @click="eventType = type"
          >
            {{ type }}
          </text>
        </view>
        <input v-model="eventContent" placeholder="补充一句，比如：今天很乖地吃完了" />
        <input v-model="mood" placeholder="心情标签，可不填" />

        <view class="photo-picker" @click="choosePhoto">
          <image v-if="selectedPhoto" class="photo-preview" :src="selectedPhoto" mode="aspectFill" />
          <view v-else class="photo-empty">
            <text>加一张宠物照片</text>
            <text class="muted">可选，用来记住这个小瞬间</text>
          </view>
        </view>
        <view v-if="selectedPhoto" class="photo-actions">
          <text @click="choosePhoto">换一张</text>
          <text class="delete-link" @click.stop="clearPhoto">移除</text>
        </view>

        <view class="button" :class="{ disabled: !canSaveEvent || savingEvent }" @click="createEvent">
          {{ savingEvent ? '保存中' : '保存宠物动态' }}
        </view>
      </view>

      <view class="section-head">
        <text>宠物时间线</text>
        <text class="count">{{ events.length }} 条</text>
      </view>
      <view class="timeline">
        <view v-for="event in events" :key="event.id" class="card glass-card event-card">
          <image v-if="eventImage(event)" class="event-photo" :src="eventImage(event)" mode="aspectFill" />
          <text class="event-title">{{ event.pet_name }} · {{ event.event_type }}</text>
          <text class="event-content">{{ event.content || '留下了一个小瞬间。' }}</text>
          <view class="event-meta">
            <text class="muted">{{ event.mood || '日常' }} · {{ formatTime(event.created_at) }}</text>
            <text class="delete-link" @click="removeEvent(event)">删除</text>
          </view>
        </view>
        <view v-if="!events.length" class="card glass-card empty">
          <text>还没有宠物动态。</text>
          <text class="muted">先记录一次喂食、散步或撒娇。</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '@/api/client'

const pets = ref<any[]>([])
const events = ref<any[]>([])
const selectedPetId = ref<number | null>(null)
const editingPetId = ref<number | null>(null)
const name = ref('')
const breed = ref('')
const birthday = ref('')
const eventType = ref('喂食')
const eventContent = ref('')
const mood = ref('')
const selectedPhoto = ref('')
const selectedName = ref('')
const selectedSize = ref(0)
const savingProfile = ref(false)
const savingEvent = ref(false)
const profileGlow = ref(false)
const eventGlow = ref(false)

const eventTypes = ['喂食', '散步', '洗澡', '撒娇', '日常']
const selectedPetName = computed(() => pets.value.find(item => item.id === selectedPetId.value)?.name || '')
const canSaveEvent = computed(() => selectedPetId.value && eventType.value && eventContent.value.trim())

onShow(load)

async function load() {
  pets.value = await request('/modules/pet/profiles')
  events.value = await request('/modules/pet/events')
  if (!selectedPetId.value && pets.value.length) {
    selectedPetId.value = pets.value[0].id
  }
}

function selectPet(id: number) {
  selectedPetId.value = id
}

function pickBirthday(event: any) {
  birthday.value = event.detail.value
}

async function choosePhoto() {
  const result = await uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera']
  })
  const file = result.tempFiles?.[0] as any
  selectedPhoto.value = result.tempFilePaths?.[0] || file?.path || ''
  selectedName.value = file?.name || `pet-event-${Date.now()}.jpg`
  selectedSize.value = Number(file?.size || 0)
}

function clearPhoto() {
  selectedPhoto.value = ''
  selectedName.value = ''
  selectedSize.value = 0
}

async function saveProfile() {
  if (!name.value.trim() || savingProfile.value) {
    uni.showToast({ title: '先写宠物名字', icon: 'none' })
    return
  }
  savingProfile.value = true
  try {
    const wasEditing = Boolean(editingPetId.value)
    const payload = {
      name: name.value.trim(),
      breed: breed.value.trim(),
      birthday: birthday.value || null
    }
    if (editingPetId.value) {
      await request(`/modules/pet/profiles/${editingPetId.value}`, {
        method: 'PUT',
        data: payload
      })
      selectedPetId.value = editingPetId.value
    } else {
      const created = await request<{ id: number }>('/modules/pet/profiles', {
        method: 'POST',
        data: payload
      })
      selectedPetId.value = created.id
    }
    resetProfileForm()
    profileGlow.value = true
    uni.showToast({ title: wasEditing ? '宠物档案已更新' : '宠物档案已保存', icon: 'none' })
    setTimeout(() => {
      profileGlow.value = false
    }, 650)
    await load()
  } finally {
    savingProfile.value = false
  }
}

function editProfile(pet: any) {
  editingPetId.value = pet.id
  name.value = pet.name || ''
  breed.value = pet.breed || ''
  birthday.value = normalizeDate(pet.birthday)
}

function resetProfileForm() {
  editingPetId.value = null
  name.value = ''
  breed.value = ''
  birthday.value = ''
}

function removeProfile(pet: any) {
  uni.showModal({
    title: '删除宠物档案',
    content: `确认删除“${pet.name}”和它的动态吗？`,
    confirmText: '删除',
    confirmColor: '#9E4D43',
    success: async result => {
      if (!result.confirm) return
      await request(`/modules/pet/profiles/${pet.id}`, { method: 'DELETE' })
      if (selectedPetId.value === pet.id) selectedPetId.value = null
      if (editingPetId.value === pet.id) resetProfileForm()
      uni.showToast({ title: '已删除', icon: 'none' })
      await load()
    }
  })
}

async function createEvent() {
  if (!canSaveEvent.value || savingEvent.value) {
    uni.showToast({ title: '先选择宠物并写一句记录', icon: 'none' })
    return
  }
  savingEvent.value = true
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
    await request('/modules/pet/events', {
      method: 'POST',
      data: {
        petId: selectedPetId.value,
        eventType: eventType.value,
        content: eventContent.value.trim(),
        mood: mood.value.trim(),
        fileId,
        localUrl: selectedPhoto.value
      }
    })
    eventContent.value = ''
    mood.value = ''
    clearPhoto()
    eventGlow.value = true
    uni.showToast({ title: '宠物动态已保存', icon: 'none' })
    setTimeout(() => {
      eventGlow.value = false
    }, 650)
    await load()
  } finally {
    savingEvent.value = false
  }
}

function eventImage(event: any) {
  return event.local_url || event.localUrl || event.object_key || ''
}

function removeEvent(event: any) {
  uni.showModal({
    title: '删除宠物动态',
    content: '确认删除这条宠物动态吗？',
    confirmText: '删除',
    confirmColor: '#9E4D43',
    success: async result => {
      if (!result.confirm) return
      await request(`/modules/pet/events/${event.id}`, { method: 'DELETE' })
      uni.showToast({ title: '已删除', icon: 'none' })
      await load()
    }
  })
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

function normalizeDate(value?: string) {
  return value?.includes('T') ? value.slice(0, 10) : value || ''
}
</script>

<style scoped lang="scss">
.pet-page {
  min-height: 100vh;
}

.content-layer,
.pet-list,
.timeline {
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
  font-size: 50rpx;
  font-weight: 700;
}

.sub {
  color: var(--color-soft);
  font-size: 27rpx;
  line-height: 1.65;
}

.composer,
.event-card,
.empty {
  display: grid;
  gap: 18rpx;
}

.form-head,
.date-picker,
.pet-card,
.section-head {
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

.button.disabled {
  opacity: 0.48;
}

.pet-card.active {
  border-color: rgba(201, 164, 106, 0.5);
  box-shadow: 0 18rpx 42rpx rgba(201, 164, 106, 0.16);
}

.avatar {
  flex: 0 0 82rpx;
  width: 82rpx;
  height: 82rpx;
  border-radius: 26rpx;
  display: grid;
  place-items: center;
  color: var(--color-text);
  background: rgba(216, 167, 160, 0.22);
  font-size: 34rpx;
  font-weight: 700;
}

.pet-main {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 8rpx;
}

.pet-name,
.event-title {
  color: var(--color-text);
  font-size: 30rpx;
  font-weight: 700;
}

.select-mark,
.action-link {
  color: var(--color-accent);
  font-size: 24rpx;
  font-weight: 700;
}

.quick-types,
.photo-actions,
.pet-actions,
.event-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.pet-actions {
  color: var(--color-accent);
  font-size: 24rpx;
  font-weight: 700;
}

.type-pill {
  padding: 12rpx 18rpx;
  border-radius: 999rpx;
  color: var(--color-muted);
  border: 1rpx solid rgba(46, 42, 39, 0.1);
  background: rgba(255, 253, 252, 0.54);
  font-size: 25rpx;
  font-weight: 700;
}

.type-pill.active {
  color: var(--color-rose);
  border-color: rgba(216, 167, 160, 0.45);
  background: rgba(216, 167, 160, 0.16);
}

.photo-picker {
  min-height: 250rpx;
  border-radius: 20rpx;
  overflow: hidden;
  border: 1rpx dashed rgba(201, 164, 106, 0.42);
  background: rgba(255, 253, 252, 0.5);
}

.photo-preview,
.event-photo {
  display: block;
  width: 100%;
  height: 100%;
}

.photo-empty {
  min-height: 250rpx;
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

.event-photo {
  height: 340rpx;
  border-radius: 18rpx;
}

.event-content {
  color: var(--color-text);
  font-size: 28rpx;
  line-height: 1.6;
}

.event-meta {
  align-items: center;
  justify-content: space-between;
}
</style>

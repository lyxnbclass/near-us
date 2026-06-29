<template>
  <view class="page romance-bg privacy-page">
    <view class="ambient-glow" />
    <view class="content-layer">
      <view class="intro">
        <text class="romance-kicker">PRIVATE BY DESIGN</text>
        <text class="headline">你的数据，应该由你决定</text>
        <text class="muted">这里不会展示日记正文和未来信正文，只导出必要的结构化记录。</text>
      </view>

      <view class="card glass-card block">
        <text class="block-title">导出我的数据</text>
        <text class="muted">生成一份包含账号、配对、相册元数据、互动记录和隐私请求的 JSON 摘要。</text>
        <view class="button" :class="{ disabled: exporting }" @click="exportData">
          {{ exporting ? '生成中' : '生成导出预览' }}
        </view>
      </view>

      <view v-if="exportPreview" class="card glass-card export-box">
        <text class="block-title">导出摘要</text>
        <text class="muted">导出时间：{{ exportPreview.exportedAt }}</text>
        <view class="export-section">
          <text class="export-label">配对信息</text>
          <text class="muted">在一起日期：{{ exportPreview.couple?.anniversary_start_date || exportPreview.couple?.paired_at || '-' }}</text>
        </view>
        <view class="export-section">
          <text class="export-label">纪念日</text>
          <view class="export-list">
            <view v-for="item in exportPreview.anniversaries || []" :key="item.id" class="export-line">
              <text>{{ item.title }}</text>
              <text class="muted">{{ item.event_date }}</text>
            </view>
          </view>
        </view>
        <view class="metric-grid">
          <view class="metric">
            <text class="metric-num">{{ exportPreview.affectionCards?.length || 0 }}</text>
            <text class="muted">心意卡片</text>
          </view>
          <view class="metric">
            <text class="metric-num">{{ exportPreview.wishes?.length || 0 }}</text>
            <text class="muted">共同愿望</text>
          </view>
          <view class="metric">
            <text class="metric-num">{{ exportPreview.futureLetters?.length || 0 }}</text>
            <text class="muted">未来信</text>
          </view>
        </view>
      </view>

      <view class="section-title">危险操作</view>
      <view class="card glass-card block danger">
        <text class="block-title">注销账号并删除数据</text>
        <text class="muted">提交后进入 7 天冷静期。正式环境应在冷静期后清理数据库记录和对象存储文件。</text>
        <view class="danger-button" :class="{ disabled: deleting }" @click="requestDeletion">
          {{ deleting ? '提交中' : '申请注销' }}
        </view>
      </view>

      <view class="section-title">请求记录</view>
      <view class="stack">
        <view v-for="item in requests" :key="item.id" class="card glass-card request-row">
          <view>
            <text class="block-title">{{ item.request_type === 'account_deletion' ? '账号注销' : item.request_type }}</text>
            <text class="muted">计划删除：{{ formatTime(item.scheduled_delete_at) }}</text>
          </view>
          <text class="status-pill">{{ item.status }}</text>
        </view>
        <view v-if="!requests.length" class="card glass-card empty">
          <text class="muted">还没有隐私请求。</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '@/api/client'

const exportPreview = ref<any>(null)
const requests = ref<any[]>([])
const exporting = ref(false)
const deleting = ref(false)

onShow(loadRequests)

async function exportData() {
  if (exporting.value) return
  exporting.value = true
  try {
    exportPreview.value = await request('/privacy/export')
    uni.showToast({ title: '导出预览已生成', icon: 'none' })
  } finally {
    exporting.value = false
  }
}

async function requestDeletion() {
  if (deleting.value) return
  uni.showModal({
    title: '确认申请注销？',
    content: '提交后会进入 7 天冷静期。',
    success: async (res) => {
      if (!res.confirm) return
      deleting.value = true
      try {
        await request('/privacy/deletion-request', { method: 'POST' })
        uni.showToast({ title: '已提交注销申请', icon: 'none' })
        await loadRequests()
      } finally {
        deleting.value = false
      }
    }
  })
}

async function loadRequests() {
  requests.value = await request('/privacy/requests')
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '待定'
}
</script>

<style scoped lang="scss">
.intro,
.block,
.export-box,
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

.block-title {
  font-size: 30rpx;
  font-weight: 600;
  color: var(--color-cocoa);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14rpx;
}

.metric {
  border-radius: 18rpx;
  background: rgba(255, 253, 250, 0.58);
  padding: 18rpx 10rpx;
  text-align: center;
}

.metric-num {
  display: block;
  color: var(--color-rose);
  font-size: 40rpx;
  font-weight: 600;
}

.export-section {
  display: grid;
  gap: 10rpx;
  padding: 16rpx 0 4rpx;
}

.export-label {
  color: var(--color-text);
  font-size: 26rpx;
  font-weight: 700;
}

.export-list {
  display: grid;
  gap: 10rpx;
}

.export-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  padding: 14rpx 16rpx;
  border-radius: 16rpx;
  background: rgba(255, 253, 250, 0.52);
  color: var(--color-text);
  font-size: 25rpx;
}

.danger {
  border-color: rgba(159, 75, 67, 0.28);
}

.danger-button {
  height: 84rpx;
  border-radius: 22rpx;
  background: rgba(159, 75, 67, 0.12);
  color: #9f4b43;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
}

.button.disabled,
.danger-button.disabled {
  opacity: 0.5;
}

.request-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18rpx;
}

.status-pill {
  flex: 0 0 auto;
  border-radius: 999rpx;
  padding: 12rpx 18rpx;
  color: var(--color-rose);
  background: rgba(217, 167, 160, 0.16);
  font-size: 24rpx;
}

.empty {
  text-align: center;
}
</style>

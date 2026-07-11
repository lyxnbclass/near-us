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
        <view class="button" @click="exportData">生成导出预览</view>
      </view>

      <view v-if="exportPreview" class="card glass-card export-box">
        <text class="block-title">导出摘要</text>
        <text class="muted">导出时间：{{ exportPreview.exportedAt }}</text>
        <view class="metric-grid">
          <view class="metric">
            <text class="metric-num">{{ exportPreview.albums?.length || 0 }}</text>
            <text class="muted">相册记录</text>
          </view>
          <view class="metric">
            <text class="metric-num">{{ exportPreview.statuses?.length || 0 }}</text>
            <text class="muted">此刻动态</text>
          </view>
          <view class="metric">
            <text class="metric-num">{{ exportPreview.diaries?.length || 0 }}</text>
            <text class="muted">日记索引</text>
          </view>
          <view class="metric">
            <text class="metric-num">{{ exportPreview.anniversaries?.length || 0 }}</text>
            <text class="muted">纪念日</text>
          </view>
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
        <view class="export-actions">
          <view class="ghost-button" @click="copyExportJson">复制 JSON</view>
          <view class="button" @click="exportData">重新生成</view>
        </view>
      </view>

      <view class="section-title">危险操作</view>
      <view class="card glass-card block danger">
        <text class="block-title">注销账号并删除数据</text>
        <text class="muted">{{ pendingDeletion ? '注销申请正在冷静期内，可以在正式删除前撤销。' : '提交后进入 7 天冷静期。正式环境应在冷静期后清理数据库记录和对象存储文件。' }}</text>
        <view v-if="pendingDeletion" class="split-actions">
          <view class="ghost-button" @click="cancelDeletion">撤销注销</view>
          <view class="danger-button muted-danger">冷静期中</view>
        </view>
        <view v-else class="danger-button" @click="requestDeletion">申请注销</view>
      </view>

      <view class="section-title">请求记录</view>
      <view class="stack">
        <view v-for="item in requests" :key="item.id" class="card glass-card request-row">
          <view>
            <text class="block-title">{{ item.request_type === 'account_deletion' ? '账号注销' : item.request_type }}</text>
            <text class="muted">计划删除：{{ formatTime(item.scheduled_delete_at) }}</text>
          </view>
          <text class="status-pill" :class="item.status">{{ statusText(item.status) }}</text>
        </view>
        <view v-if="!requests.length" class="card glass-card empty">
          <text class="muted">还没有隐私请求。</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getErrorMessage, request } from '@/api/client'

const exportPreview = ref<any>(null)
const requests = ref<any[]>([])
const pendingDeletion = computed(() => requests.value.some(item => item.request_type === 'account_deletion' && item.status === 'pending'))
const exportJson = computed(() => exportPreview.value ? JSON.stringify(exportPreview.value, null, 2) : '')

onShow(loadRequests)

async function exportData() {
  try {
    exportPreview.value = await request('/privacy/export')
    uni.showToast({ title: '导出预览已生成', icon: 'none' })
  } catch (error: any) {
    uni.showToast({ title: getErrorMessage(error, '暂时生成不了导出预览'), icon: 'none' })
  }
}

function copyExportJson() {
  if (!exportJson.value) return
  uni.setClipboardData({
    data: exportJson.value,
    success: () => uni.showToast({ title: 'JSON 已复制', icon: 'none' })
  })
}

async function requestDeletion() {
  if (pendingDeletion.value) {
    uni.showToast({ title: '已有待处理的注销申请', icon: 'none' })
    return
  }
  uni.showModal({
    title: '确认申请注销？',
    content: '提交后会进入 7 天冷静期。',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await request('/privacy/deletion-request', { method: 'POST' })
        uni.showToast({ title: '已提交注销申请', icon: 'none' })
        await loadRequests()
      } catch (error: any) {
        uni.showToast({ title: getErrorMessage(error, '暂时不能提交注销申请'), icon: 'none' })
      }
    }
  })
}

async function cancelDeletion() {
  uni.showModal({
    title: '撤销注销申请？',
    content: '撤销后账号会恢复正常状态，可以继续使用当前空间。',
    confirmText: '撤销',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await request('/privacy/deletion-request/cancel', { method: 'POST' })
        uni.showToast({ title: '已撤销注销申请', icon: 'none' })
        await loadRequests()
      } catch (error: any) {
        uni.showToast({ title: getErrorMessage(error, '暂时不能撤销注销申请'), icon: 'none' })
      }
    }
  })
}

async function loadRequests() {
  try {
    requests.value = await request('/privacy/requests')
  } catch (error: any) {
    uni.showToast({ title: getErrorMessage(error, '暂时加载不了隐私请求'), icon: 'none' })
  }
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '待定'
}

function statusText(status: string) {
  const map: Record<string, string> = {
    pending: '冷静期',
    cancelled: '已撤销',
    completed: '已完成'
  }
  return map[status] || status
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

.export-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
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

.split-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}

.ghost-button {
  height: 84rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text);
  border: 1rpx solid var(--color-line);
  background: rgba(255, 253, 252, 0.58);
  font-weight: 600;
}

.muted-danger {
  opacity: 0.68;
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

.status-pill.cancelled {
  color: var(--color-muted);
  background: rgba(46, 42, 39, 0.08);
}

.status-pill.completed {
  color: var(--color-cocoa);
  background: rgba(201, 164, 106, 0.18);
}

.empty {
  text-align: center;
}
</style>

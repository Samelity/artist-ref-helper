<template>
  <section class="view">
    <PageHeader
      title="相似图审查"
      description="按 CLIP 向量找出重编码、缩放或轻度裁剪的同一张图。这里只列出候选，保留与删除都由你确认，不会自动处理。"
    >
      <template #actions>
        <n-select
          v-model:value="threshold"
          :options="thresholdOptions"
          size="small"
          class="threshold-select"
          :disabled="scanning || deletingKey !== null"
        />
        <n-button
          type="primary"
          :loading="scanning"
          :disabled="deletingKey !== null"
          @click="startScan"
        >
          <template #icon>
            <AppIcon name="search" :size="15" />
          </template>
          {{ hasResult ? '重新扫描' : '开始扫描' }}
        </n-button>
      </template>
    </PageHeader>

    <StatusBar :text="statusText" :loading="scanning" />

    <EmptyState
      v-if="!scanning && !hasResult"
      text="点击右上角「开始扫描」，从已建立的 CLIP 索引中查找相似图片"
    />
    <EmptyState
      v-else-if="!scanning && hasResult && !groups.length"
      text="没有发现达到当前阈值的相似图片，可以降低阈值后重新扫描"
    />

    <div v-if="groups.length" class="group-list">
      <article v-for="(group, index) in groups" :key="group.key" class="group-card">
        <header class="group-head">
          <div class="group-title">
            <span class="group-index">组 {{ index + 1 }}</span>
            <span class="group-meta">
              {{ group.images.length }} 张 · 相似度
              {{ formatScore(group.minScore) }}–{{ formatScore(group.maxScore) }}
            </span>
          </div>
          <div class="group-actions">
            <n-button
              quaternary
              size="small"
              :disabled="!selectedInGroup(group).length || scanning || deletingKey !== null"
              @click="clearGroup(group)"
            >
              清空选择
            </n-button>
            <n-button
              quaternary
              size="small"
              :disabled="scanning || deletingKey !== null"
              @click="selectOthers(group)"
            >
              仅保留建议项
            </n-button>
            <n-button
              type="error"
              size="small"
              :loading="deletingKey === group.key"
              :disabled="!selectedInGroup(group).length || scanning || deletingKey !== null"
              @click="requestDelete(group)"
            >
              删除所选{{ selectedInGroup(group).length ? ` ${selectedInGroup(group).length}` : '' }}
            </n-button>
          </div>
        </header>

        <div class="member-grid">
          <div
            v-for="image in group.images"
            :key="image.id"
            class="member"
            :class="{
              'is-keep': image.id === group.suggestedKeepId,
              'is-picked': isSelected(image.id),
            }"
          >
            <button
              type="button"
              class="thumb"
              :title="image.fileName || ''"
              @click="preview(group, image.id)"
            >
              <img :src="imageUrl(image.filePath)" :alt="image.fileName || ''" loading="lazy" />
              <span class="score">{{ formatScore(image.score) }}</span>
              <span v-if="image.id === group.suggestedKeepId" class="badge keep-badge">
                建议保留
              </span>
              <span v-else-if="isSelected(image.id)" class="badge del-badge">待删除</span>
            </button>

            <div class="member-info">
              <div v-if="image.id === group.suggestedKeepId" class="keep-row">
                <AppIcon name="check" :size="14" :stroke-width="2.4" />
                <span class="file-name" :title="image.fileName || ''">
                  {{ image.fileName || '(无文件名)' }}
                </span>
              </div>
              <div v-else class="check-row">
                <n-checkbox
                  :checked="isSelected(image.id)"
                  :disabled="scanning || deletingKey !== null"
                  @update:checked="(checked) => onChecked(image.id, checked)"
                />
                <span class="file-name" :title="image.fileName || ''">
                  {{ image.fileName || '(无文件名)' }}
                </span>
              </div>
              <p class="member-meta">
                #{{ image.id }}
                <template v-if="sourceLabel(image)"> · {{ sourceLabel(image) }}</template>
              </p>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NCheckbox, NSelect, useDialog, useMessage } from 'naive-ui'
import PageHeader from '@/components/layout/PageHeader.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { deleteImages, scanDuplicates } from '@/api/images'
import type { DuplicateGroup, DuplicateImage } from '@/api/images'
import { useLightboxStore } from '@/stores/lightbox'
import { imageUrl } from '@/utils/imageUrl'
import { formatScore } from '@/utils/format'

defineOptions({ name: 'DuplicatesView' })

interface ReviewGroup extends DuplicateGroup {
  key: number
}

const message = useMessage()
const dialog = useDialog()
const lightbox = useLightboxStore()

const threshold = ref(0.98)
const thresholdOptions = [
  { label: '0.995 · 极严格', value: 0.995 },
  { label: '0.99 · 严格', value: 0.99 },
  { label: '0.98 · 推荐', value: 0.98 },
  { label: '0.95 · 宽松', value: 0.95 },
]

const groups = ref<ReviewGroup[]>([])
const selectedIds = ref<Set<number>>(new Set())
const scanning = ref(false)
const deletingKey = ref<number | null>(null)
const hasResult = ref(false)
const statusText = ref('尚未扫描，点击「开始扫描」查找相似图片')

async function startScan() {
  if (scanning.value || deletingKey.value !== null) return
  scanning.value = true
  statusText.value = '正在扫描 CLIP 索引，图库较大时可能需要数十秒…'
  try {
    const data = await scanDuplicates(threshold.value)
    groups.value = data.groups.map((group, index) => ({ key: index, ...group }))
    selectedIds.value = new Set(
      data.groups.flatMap((group) =>
        group.images
          .filter((image) => image.id !== group.suggestedKeepId)
          .map((image) => image.id),
      ),
    )
    hasResult.value = true
    if (data.totalGroups > 0) {
      statusText.value =
        `扫描完成：共 ${data.totalGroups} 组、${data.totalImages} 张候选图片` +
        `（阈值 ${data.threshold}，耗时 ${formatElapsed(data.elapsedMs)}${data.cached ? '，缓存' : ''}）`
      message.success(`发现 ${data.totalGroups} 组相似图片`)
    } else {
      statusText.value = `扫描完成：未发现相似图片（阈值 ${data.threshold}）`
      message.info('未发现相似图片')
    }
  } catch (e: unknown) {
    statusText.value = '扫描失败：' + errorText(e)
    message.error('扫描失败：' + errorText(e), { duration: 8000 })
  } finally {
    scanning.value = false
  }
}

function isSelected(imageId: number) {
  return selectedIds.value.has(imageId)
}

function onChecked(imageId: number, checked: boolean) {
  const next = new Set(selectedIds.value)
  if (checked) {
    next.add(imageId)
  } else {
    next.delete(imageId)
  }
  selectedIds.value = next
}

function selectedInGroup(group: ReviewGroup) {
  return group.images.filter((image) => selectedIds.value.has(image.id))
}

function selectOthers(group: ReviewGroup) {
  const next = new Set(selectedIds.value)
  for (const image of group.images) {
    if (image.id !== group.suggestedKeepId) next.add(image.id)
  }
  selectedIds.value = next
}

function clearGroup(group: ReviewGroup) {
  const next = new Set(selectedIds.value)
  for (const image of group.images) next.delete(image.id)
  selectedIds.value = next
}

function requestDelete(group: ReviewGroup) {
  const targets = selectedInGroup(group)
  if (!targets.length || deletingKey.value !== null) return
  const keep = group.images.find((image) => image.id === group.suggestedKeepId)
  dialog.warning({
    title: '删除相似图片',
    content:
      `将删除选中的 ${targets.length} 张图片` +
      (keep ? `，保留「${keep.fileName || '建议保留项'}」` : '') +
      '。文件会被永久删除，并从所有灵感板中移除。确定继续吗？',
    positiveText: `删除 ${targets.length} 张`,
    negativeText: '取消',
    onPositiveClick: () => performDelete(group, targets.map((image) => image.id)),
  })
}

async function performDelete(group: ReviewGroup, ids: number[]) {
  deletingKey.value = group.key
  try {
    const result = await deleteImages(ids)
    const removed = new Set(ids)
    group.images = group.images.filter((image) => !removed.has(image.id))
    const next = new Set(selectedIds.value)
    for (const id of ids) next.delete(id)
    selectedIds.value = next
    if (group.images.length < 2) {
      groups.value = groups.value.filter((item) => item.key !== group.key)
    }
    refreshStatus()

    const parts = [`已删除 ${result.deleted} 张图片`]
    if (result.missing.length) parts.push(`${result.missing.length} 张已不存在`)
    if (result.errors.length) {
      message.warning(parts.join('，') + '；' + result.errors[0], { duration: 6000 })
    } else {
      message.success(parts.join('，'))
    }
    window.dispatchEvent(new CustomEvent('library:refresh'))
  } catch (e: unknown) {
    message.error('删除失败：' + errorText(e))
  } finally {
    deletingKey.value = null
  }
}

function refreshStatus() {
  if (!hasResult.value) return
  const total = groups.value.reduce((sum, group) => sum + group.images.length, 0)
  statusText.value = groups.value.length
    ? `当前剩余 ${groups.value.length} 组、${total} 张候选图片（阈值 ${threshold.value}）`
    : '没有剩余候选组'
}

function preview(group: ReviewGroup, imageId: number) {
  const index = group.images.findIndex((image) => image.id === imageId)
  lightbox.show(
    group.images.map((image) => ({
      src: imageUrl(image.filePath),
      title: image.fileName || '(无文件名)',
      subtitle: `相似度 ${formatScore(image.score)} · #${image.id}`,
    })),
    Math.max(index, 0),
  )
}

function sourceLabel(image: DuplicateImage) {
  if (image.photographer) return `来源 · ${image.photographer}`
  if (image.source === 'pixabay') return '来源 · Pixabay'
  if (image.source === 'pexels') return '来源 · Pexels'
  if (image.source === 'url') return '来源 · URL'
  return ''
}

function formatElapsed(ms: number) {
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
}

function errorText(e: unknown) {
  const data = (e as { response?: { data?: { detail?: string; error?: string } } })?.response?.data
  if (data?.detail) return data.detail
  if (data?.error) return data.error
  return e instanceof Error ? e.message : String(e)
}
</script>

<style scoped>
.threshold-select {
  width: 148px;
}

.group-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin-top: 4px;
}

.group-card {
  padding: 16px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.group-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.group-index {
  font-size: 14px;
  font-weight: 650;
  color: var(--text-1);
}

.group-meta {
  font-size: 12px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}

.group-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.member-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(176px, 1fr));
  gap: 14px;
}

.member {
  overflow: hidden;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}

.member.is-picked {
  border-color: var(--danger);
  box-shadow: 0 0 0 3px var(--danger-soft);
}

.member.is-keep {
  border-color: var(--success);
  box-shadow: 0 0 0 3px var(--success-soft);
}

.thumb {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  padding: 0;
  border: none;
  background: var(--bg-hover);
  overflow: hidden;
  cursor: zoom-in;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s var(--ease);
}

.member:hover .thumb img {
  transform: scale(1.03);
}

.score {
  position: absolute;
  top: 7px;
  left: 7px;
  padding: 2px 7px;
  border-radius: var(--radius-full);
  background: rgba(17, 24, 39, 0.72);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.badge {
  position: absolute;
  right: 7px;
  bottom: 7px;
  padding: 3px 8px;
  border-radius: var(--radius-full);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.01em;
  backdrop-filter: blur(4px);
}

.keep-badge {
  background: rgba(5, 150, 105, 0.92);
}

.del-badge {
  background: rgba(220, 38, 38, 0.9);
}

.member-info {
  padding: 9px 10px 10px;
}

.check-row,
.keep-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.keep-row {
  color: var(--success);
}

.file-name {
  min-width: 0;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.member-meta {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--text-4);
  font-variant-numeric: tabular-nums;
}

@media (max-width: 640px) {
  .group-head {
    align-items: flex-start;
  }

  .group-actions {
    width: 100%;
  }

  .member-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }
}
</style>

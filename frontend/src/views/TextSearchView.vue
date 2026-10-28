<template>
  <section class="view">
    <PageHeader
      title="文搜图"
      description="用自然语言描述画面，CLIP 会在本地图库中找到语义最接近的参考图。"
    />

    <div class="surface search-panel">
      <div class="search-row">
        <n-input
          v-model:value="query"
          size="large"
          placeholder="输入描述，如：雨夜街道、霓虹灯、低角度"
          clearable
          @keydown.enter="search"
        >
          <template #prefix>
            <AppIcon name="search" :size="18" />
          </template>
        </n-input>
        <n-button type="primary" size="large" :loading="loading" :disabled="!query.trim()" @click="search">
          搜索
        </n-button>
      </div>

      <div class="chips">
        <button
          v-for="s in suggestions"
          :key="s"
          type="button"
          class="chip"
          @click="applySuggestion(s)"
        >
          {{ s }}
        </button>
      </div>

      <div class="params">
        <span class="params-label">检索参数</span>
        <TopKThreshold :default-top-k="12" :default-threshold="0.2" @update="onParamsUpdate" />
      </div>
    </div>

    <StatusBar :text="statusText" :loading="loading" />
    <SearchResultGrid :items="results" :empty-text="emptyText" @add-to-board="openBoardPicker" />

    <BoardPicker
      v-if="pickerVisible"
      :image-id="pickerImageId"
      :anchor="pickerAnchor"
      @close="pickerVisible = false"
      @added="onAddedToBoard"
    />
  </section>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { NInput, NButton } from 'naive-ui'
import PageHeader from '@/components/layout/PageHeader.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import TopKThreshold from '@/components/search/TopKThreshold.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import SearchResultGrid from '@/components/search/SearchResultGrid.vue'
import BoardPicker from '@/components/board/BoardPicker.vue'
import { searchByText } from '@/api/images'
import type { ImageItem } from '@/types/image'

defineOptions({ name: 'TextSearchView' })

const query = ref('雨夜街道、霓虹灯')
const topK = ref(12)
const threshold = ref(0.2)
const loading = ref(false)
const searched = ref(false)
const statusText = ref('输入关键词开始检索')
const results = ref<ImageItem[]>([])

const suggestions = ['雨夜街道', '霓虹灯', '低角度', '赛博朋克', '复古胶片', '厚涂质感']

const emptyText = computed(() =>
  searched.value
    ? '未找到相似图片，试试换个关键词或调低相似度阈值'
    : '输入描述后回车，开始检索本地图库',
)

const pickerVisible = ref(false)
const pickerImageId = ref<number>(0)
const pickerAnchor = ref<{ left: number; top: number }>({ left: 0, top: 0 })

function onParamsUpdate(p: { topK: number; threshold: number }) {
  topK.value = p.topK
  threshold.value = p.threshold
}

function applySuggestion(s: string) {
  query.value = s
  search()
}

async function search() {
  const q = query.value.trim()
  if (!q) return
  loading.value = true
  statusText.value = '文搜图检索中...'
  try {
    results.value = await searchByText({ query: q, topK: topK.value, threshold: threshold.value })
    searched.value = true
    statusText.value = results.value.length
      ? `找到 ${results.value.length} 张相似图片`
      : '未找到相似图片，试试换个关键词或调低 threshold'
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    statusText.value = '文搜图失败: ' + msg
    results.value = []
    searched.value = true
  } finally {
    loading.value = false
  }
}

function openBoardPicker(payload: { imageId: number; event: MouseEvent }) {
  const rect = (payload.event.currentTarget as HTMLElement).getBoundingClientRect()
  pickerImageId.value = payload.imageId
  pickerAnchor.value = { left: rect.left, top: rect.bottom + 4 }
  pickerVisible.value = true
}

function onAddedToBoard() {
  statusText.value = '已添加到灵感板'
}
</script>

<style scoped>
.search-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px 20px;
}

.search-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.search-row :deep(.n-input) {
  flex: 1;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 5px 12px;
  font-family: inherit;
  font-size: 12.5px;
  color: var(--text-2);
  background: var(--bg-hover);
  border: none;
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease);
}

.chip:hover {
  background: var(--brand-soft);
  color: var(--brand-pressed);
}

.params {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 14px;
  border-top: 1px dashed var(--border);
}

.params-label {
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--text-4);
}

@media (max-width: 620px) {
  .search-row {
    flex-wrap: wrap;
  }

  .search-row :deep(.n-input) {
    flex: 1 1 100%;
  }

  .search-row :deep(.n-button) {
    width: 100%;
  }
}
</style>

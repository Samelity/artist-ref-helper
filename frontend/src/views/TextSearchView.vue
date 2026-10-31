<template>
  <section class="view">
    <PageHeader
      title="文搜图"
      description="用自然语言描述画面，CLIP 会在本地图库中找到语义最接近的参考图。"
    >
      <template #actions>
        <SelectionToggle
          :mode="selectionMode"
          :disabled="!results.length"
          @enter="enterSelection"
          @exit="exitSelection"
        />
      </template>
    </PageHeader>

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

    <SelectionBar v-if="selectionMode" :count="selectedCount">
      <n-button quaternary size="small" :disabled="allSelected" @click="selectAll(currentResultIds)">
        全选本页
      </n-button>
      <n-button quaternary size="small" :disabled="!selectedCount" @click="clearSelection">
        清空
      </n-button>
      <n-button type="primary" size="small" :disabled="!selectedCount" @click="openBatchPicker">
        加入灵感板
      </n-button>
    </SelectionBar>

    <StatusBar :text="statusText" :loading="loading" />
    <SearchResultGrid
      :items="results"
      :empty-text="emptyText"
      :selection-mode="selectionMode"
      :selected-ids="selectedIds"
      @add-to-board="openBoardPicker"
      @toggle-select="toggleSelect"
    />

    <BoardPicker
      v-if="pickerVisible"
      :image-ids="pickerImageIds"
      :anchor="pickerAnchor"
      @close="pickerVisible = false"
      @added="onAddedToBoard"
    />
  </section>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { NInput, NButton, useMessage } from 'naive-ui'
import PageHeader from '@/components/layout/PageHeader.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import TopKThreshold from '@/components/search/TopKThreshold.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import SelectionBar from '@/components/common/SelectionBar.vue'
import SelectionToggle from '@/components/common/SelectionToggle.vue'
import SearchResultGrid from '@/components/search/SearchResultGrid.vue'
import BoardPicker from '@/components/board/BoardPicker.vue'
import { useImageSelection } from '@/composables/useImageSelection'
import { searchByText } from '@/api/images'
import type { BatchAddResult } from '@/types/board'
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

const message = useMessage()
const {
  mode: selectionMode,
  ids: selectedIds,
  count: selectedCount,
  enter: enterSelection,
  exit: exitSelection,
  toggle: toggleSelect,
  selectAll,
  clear: clearSelection,
  isSelected,
} = useImageSelection()

const pickerVisible = ref(false)
const pickerImageIds = ref<number[]>([])
const pickerAnchor = ref<{ left: number; top: number }>({ left: 0, top: 0 })

const currentResultIds = computed(() => results.value.map((item) => item.id))
const allSelected = computed(
  () => currentResultIds.value.length > 0 && currentResultIds.value.every((id) => isSelected(id)),
)

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
  exitSelection()
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

function openPickerAt(event: MouseEvent) {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  pickerAnchor.value = { left: rect.left, top: rect.bottom + 4 }
  pickerVisible.value = true
}

function openBoardPicker(payload: { imageId: number; event: MouseEvent }) {
  pickerImageIds.value = [payload.imageId]
  openPickerAt(payload.event)
}

function openBatchPicker(event: MouseEvent) {
  pickerImageIds.value = Array.from(selectedIds.value)
  openPickerAt(event)
}

function onAddedToBoard(result: BatchAddResult) {
  pickerVisible.value = false
  if (result.added > 0) {
    message.success(
      `已添加 ${result.added} 张到「${result.board.name}」` +
        (result.skipped > 0 ? `，跳过 ${result.skipped} 张` : ''),
    )
  } else {
    message.info(`所选图片均已在「${result.board.name}」中`)
  }
  exitSelection()
  statusText.value = `已添加到「${result.board.name}」`
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

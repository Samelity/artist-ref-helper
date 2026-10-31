<template>
  <section class="view">
    <PageHeader
      title="图搜图"
      description="上传或拖入一张参考图，检索本地图库中视觉风格相近的图片。"
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
      <ImageDropzone ref="dropzoneRef" @select="onFileSelect" />
      <div class="panel-footer">
        <div class="params">
          <span class="params-label">检索参数</span>
          <TopKThreshold :default-top-k="12" :default-threshold="0.2" @update="onParamsUpdate" />
        </div>
        <n-button type="primary" :loading="loading" :disabled="!selectedFile" @click="search">
          开始检索
        </n-button>
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
import { NButton, useMessage } from 'naive-ui'
import PageHeader from '@/components/layout/PageHeader.vue'
import ImageDropzone from '@/components/image-search/ImageDropzone.vue'
import TopKThreshold from '@/components/search/TopKThreshold.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import SelectionBar from '@/components/common/SelectionBar.vue'
import SelectionToggle from '@/components/common/SelectionToggle.vue'
import SearchResultGrid from '@/components/search/SearchResultGrid.vue'
import BoardPicker from '@/components/board/BoardPicker.vue'
import { useImageSelection } from '@/composables/useImageSelection'
import { searchByImage } from '@/api/images'
import type { BatchAddResult } from '@/types/board'
import type { ImageItem } from '@/types/image'

defineOptions({ name: 'ImageSearchView' })

const dropzoneRef = ref<InstanceType<typeof ImageDropzone> | null>(null)
const selectedFile = ref<File | null>(null)
const topK = ref(12)
const threshold = ref(0.2)
const loading = ref(false)
const searched = ref(false)
const statusText = ref('选择或拖拽一张图片开始检索')
const results = ref<ImageItem[]>([])

const emptyText = computed(() =>
  searched.value ? '未找到相似图片，试试降低相似度阈值' : '上传参考图后开始检索',
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

function onFileSelect(file: File) {
  selectedFile.value = file
}

function onParamsUpdate(p: { topK: number; threshold: number }) {
  topK.value = p.topK
  threshold.value = p.threshold
}

async function search() {
  if (!selectedFile.value) {
    statusText.value = '请先选择一张图片'
    return
  }
  exitSelection()
  loading.value = true
  statusText.value = '图搜图检索中...'
  try {
    results.value = await searchByImage(selectedFile.value, topK.value, threshold.value)
    searched.value = true
    statusText.value = results.value.length
      ? `找到 ${results.value.length} 张相似图片`
      : '未找到相似图片'
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    statusText.value = '图搜图失败: ' + msg
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
  gap: 16px;
  padding: 18px 20px 20px;
}

.panel-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-top: 14px;
  border-top: 1px dashed var(--border);
}

.params {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.params-label {
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--text-4);
}

@media (max-width: 620px) {
  .panel-footer :deep(.n-button) {
    width: 100%;
  }
}
</style>

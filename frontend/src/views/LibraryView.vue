<template>
  <section class="view">
    <PageHeader
      title="图库浏览"
      description="浏览本地图库的全部图片，按入库顺序倒序排列，最新导入的图片排在最前。"
    >
      <template #actions>
        <SelectionToggle
          :mode="selectionMode"
          :disabled="!images.length"
          @enter="enterSelection"
          @exit="exitSelection"
        />
      </template>
    </PageHeader>

    <SelectionBar v-if="selectionMode" :count="selectedCount">
      <n-button quaternary size="small" :disabled="allSelected" @click="selectAll(currentPageIds)">
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
      :items="images"
      :empty-text="emptyText"
      :selection-mode="selectionMode"
      :selected-ids="selectedIds"
      @add-to-board="openBoardPicker"
      @toggle-select="toggleSelect"
    />

    <PaginationBar
      v-if="total > 0"
      :page="page"
      :total-pages="totalPages"
      :total-results="total"
      @change="onPageChange"
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
import { computed, onActivated, onMounted, ref } from 'vue'
import { NButton, useMessage } from 'naive-ui'
import PageHeader from '@/components/layout/PageHeader.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import SelectionBar from '@/components/common/SelectionBar.vue'
import SelectionToggle from '@/components/common/SelectionToggle.vue'
import SearchResultGrid from '@/components/search/SearchResultGrid.vue'
import BoardPicker from '@/components/board/BoardPicker.vue'
import { useImageSelection } from '@/composables/useImageSelection'
import { browseImages } from '@/api/images'
import type { BatchAddResult } from '@/types/board'
import type { ImageItem } from '@/types/image'

defineOptions({ name: 'LibraryView' })

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

const images = ref<ImageItem[]>([])
const page = ref(1)
const total = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const statusText = ref('图库加载中...')

const emptyText = computed(() =>
  loading.value ? '图库加载中...' : '图库还没有图片，去「导入图片」添加一些吧',
)

const pickerVisible = ref(false)
const pickerImageIds = ref<number[]>([])
const pickerAnchor = ref<{ left: number; top: number }>({ left: 0, top: 0 })

const currentPageIds = computed(() => images.value.map((item) => item.id))
const allSelected = computed(
  () => currentPageIds.value.length > 0 && currentPageIds.value.every((id) => isSelected(id)),
)

let loadSeq = 0

async function load() {
  const seq = ++loadSeq
  loading.value = true
  statusText.value = '图库加载中...'
  try {
    const data = await browseImages(page.value)
    if (seq !== loadSeq) return
    images.value = data.items
    page.value = data.page
    total.value = data.total
    totalPages.value = data.totalPages
    statusText.value = data.total > 0
      ? `第 ${data.page} / ${data.totalPages} 页，共 ${data.total} 张图片`
      : '图库暂无图片'
  } catch (e: unknown) {
    if (seq !== loadSeq) return
    const msg = e instanceof Error ? e.message : String(e)
    statusText.value = '加载失败: ' + msg
    images.value = []
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  window.scrollTo({ top: 0, behavior: 'smooth' })
  load()
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
}

onMounted(load)

let firstActivation = true
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  // 从导入页返回时图库可能已变化，刷新当前页
  exitSelection()
  load()
})
</script>

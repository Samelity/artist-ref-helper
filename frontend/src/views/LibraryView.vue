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
      <n-button
        type="error"
        size="small"
        :loading="deleting"
        :disabled="!selectedCount || deleting"
        @click="requestBatchDelete"
      >
        <template #icon>
          <AppIcon name="trash" :size="14" />
        </template>
        删除所选
      </n-button>
    </SelectionBar>

    <StatusBar :text="statusText" :loading="loading" />
    <SearchResultGrid
      :items="images"
      :empty-text="emptyText"
      :selection-mode="selectionMode"
      :selected-ids="selectedIds"
      allow-delete
      @add-to-board="openBoardPicker"
      @toggle-select="toggleSelect"
      @delete="requestSingleDelete"
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
import { computed, onActivated, onBeforeUnmount, onMounted, ref } from 'vue'
import { NButton, useDialog, useMessage } from 'naive-ui'
import PageHeader from '@/components/layout/PageHeader.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import SelectionBar from '@/components/common/SelectionBar.vue'
import SelectionToggle from '@/components/common/SelectionToggle.vue'
import SearchResultGrid from '@/components/search/SearchResultGrid.vue'
import BoardPicker from '@/components/board/BoardPicker.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { useImageSelection } from '@/composables/useImageSelection'
import { browseImages, deleteImages } from '@/api/images'
import type { BatchAddResult } from '@/types/board'
import type { ImageItem } from '@/types/image'

defineOptions({ name: 'LibraryView' })

const message = useMessage()
const dialog = useDialog()
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
const deleting = ref(false)
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

function requestBatchDelete() {
  const count = selectedCount.value
  if (!count || deleting.value) return
  dialog.warning({
    title: '删除图片',
    content: `确定要删除选中的 ${count} 张图片吗？图片文件会被永久删除，并从所有灵感板中移除。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: () => performDelete(Array.from(selectedIds.value)),
  })
}

function requestSingleDelete(imageId: number) {
  if (deleting.value) return
  const image = images.value.find((item) => item.id === imageId)
  const label = image?.fileName ? `「${image.fileName}」` : '这张图片'
  dialog.warning({
    title: '删除图片',
    content: `确定要删除${label}吗？图片文件会被永久删除，并从所有灵感板中移除。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: () => performDelete([imageId]),
  })
}

async function performDelete(ids: number[]) {
  if (deleting.value || !ids.length) return
  deleting.value = true
  try {
    const result = await deleteImages(ids)
    const parts = [`已删除 ${result.deleted} 张图片`]
    if (result.missing.length) {
      parts.push(`${result.missing.length} 张已不存在`)
    }
    if (result.errors.length) {
      message.warning(parts.join('，') + '；' + result.errors[0], { duration: 6000 })
    } else {
      message.success(parts.join('，'))
    }
    exitSelection()
    await load()
  } catch (e: unknown) {
    message.error('删除失败: ' + errorText(e))
  } finally {
    deleting.value = false
  }
}

function errorText(e: unknown): string {
  const detail = (e as { response?: { data?: { detail?: string } } })?.response?.data?.detail
  if (detail) return detail
  return e instanceof Error ? e.message : String(e)
}

function onLibraryRefresh() {
  if (!loading.value) load()
}

onMounted(() => {
  window.addEventListener('library:refresh', onLibraryRefresh)
  load()
})

onBeforeUnmount(() => {
  window.removeEventListener('library:refresh', onLibraryRefresh)
})

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

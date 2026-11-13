<template>
  <div v-if="board" class="board-detail">
    <div class="detail-header">
      <div class="title-wrap">
        <h2>{{ board.name }}</h2>
        <span class="meta">{{ board.imageCount }} 张图片</span>
      </div>
      <div class="header-actions">
        <SelectionToggle
          :mode="selectionMode"
          :disabled="!(board.images && board.images.length)"
          @enter="enterSelection"
          @exit="exitSelection"
        />
        <n-button size="small" :theme-overrides="brandSoftButtonTheme" @click="openEdit">
          <template #icon>
            <AppIcon name="edit" :size="13" />
          </template>
          编辑
        </n-button>
      </div>
    </div>
    <p v-if="board.description" class="desc">{{ board.description }}</p>
    <StatusBar v-if="pasteStatus" :text="pasteStatus" :loading="pasting" />
    <SelectionBar v-if="selectionMode" :count="selectedCount">
      <n-button quaternary size="small" :disabled="allPageSelected" @click="selectAll(pageImageIds)">
        全选本页
      </n-button>
      <n-button quaternary size="small" :disabled="allSelected" @click="selectAll(boardImageIds)">
        全选本板
      </n-button>
      <n-button quaternary size="small" :disabled="!selectedCount" @click="clearSelection">
        清空
      </n-button>
      <n-button
        type="primary"
        size="small"
        :disabled="!selectedCount"
        @click="openClassifyPicker"
      >
        <template #icon>
          <AppIcon name="boards" :size="14" />
        </template>
        归类到…
      </n-button>
      <n-button
        type="error"
        size="small"
        :loading="removing"
        :disabled="!selectedCount || removing"
        @click="requestBatchRemove"
      >
        <template #icon>
          <AppIcon name="trash" :size="14" />
        </template>
        移出灵感板
      </n-button>
    </SelectionBar>
    <div v-if="pagedImages.length" class="board-grid">
      <BoardCard
        v-for="(img, i) in pagedImages"
        :key="img.id"
        :image="img"
        :selection-mode="selectionMode"
        :selected="isSelected(img.id)"
        :drag-ids="dragIdsFor(img.id)"
        @preview="openPreview(i)"
        @remove="removeImage(img.id)"
        @toggle-select="toggleSelect(img.id)"
      />
    </div>
    <EmptyState v-else text="灵感板暂无图片，可点击 + 添加，或直接 Ctrl+V 粘贴上传" />

    <PaginationBar
      v-if="totalPages > 1"
      :page="page"
      :total-pages="totalPages"
      :total-results="boardImageIds.length"
      @change="onPageChange"
    />

    <BoardPicker
      v-if="pickerVisible"
      :image-ids="pickerImageIds"
      :anchor="pickerAnchor"
      :exclude-board-id="board.id"
      title="归类到灵感板"
      @close="pickerVisible = false"
      @added="onClassified"
    />

    <n-modal
      v-model:show="editVisible"
      preset="card"
      title="编辑灵感板"
      :style="{ width: '440px', maxWidth: 'calc(100vw - 32px)' }"
      :bordered="false"
      :closable="!saving"
      :mask-closable="!saving"
    >
      <div class="edit-form">
        <label class="field">
          <span class="field-label">名称</span>
          <n-input
            v-model:value="editName"
            placeholder="灵感板名称"
            maxlength="60"
            show-count
            :disabled="saving"
          />
        </label>
        <label class="field">
          <span class="field-label">描述</span>
          <n-input
            v-model:value="editDescription"
            type="textarea"
            placeholder="描述（可选）"
            maxlength="200"
            show-count
            :autosize="{ minRows: 3, maxRows: 6 }"
            :disabled="saving"
          />
        </label>
      </div>
      <template #footer>
        <div class="edit-actions">
          <n-button size="small" :disabled="saving" @click="editVisible = false">取消</n-button>
          <n-button
            type="primary"
            size="small"
            :loading="saving"
            :disabled="!editName.trim()"
            @click="saveEdit"
          >
            保存
          </n-button>
        </div>
      </template>
    </n-modal>
  </div>
  <EmptyState v-else text="加载失败" />
</template>

<script setup lang="ts">
import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { NButton, NInput, NModal, useDialog, useMessage } from 'naive-ui'
import BoardCard from './BoardCard.vue'
import BoardPicker from './BoardPicker.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import SelectionBar from '@/components/common/SelectionBar.vue'
import SelectionToggle from '@/components/common/SelectionToggle.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import { useBoardsStore } from '@/stores/boards'
import { useLightboxStore } from '@/stores/lightbox'
import { useImageSelection } from '@/composables/useImageSelection'
import { useBoardPasteUpload } from '@/composables/useBoardPasteUpload'
import { brandSoftButtonTheme } from '@/ui/buttonTheme'
import { imageUrl } from '@/utils/imageUrl'
import {
  clipTimestamp,
  filesFromClipboard,
  isEditableTarget,
  isSupportedImage,
  normalizePastedImage,
} from '@/utils/clipboard'
import type { BatchAddResult, Board } from '@/types/board'

const props = defineProps<{ board: Board | null }>()
const store = useBoardsStore()
const lightbox = useLightboxStore()
const message = useMessage()
const dialog = useDialog()
const { pasting, pasteStatus, uploadToBoard } = useBoardPasteUpload()

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

const PAGE_SIZE = 20

const editVisible = ref(false)
const editName = ref('')
const editDescription = ref('')
const saving = ref(false)
const removing = ref(false)

const pickerVisible = ref(false)
const pickerImageIds = ref<number[]>([])
const pickerAnchor = ref<{ left: number; top: number }>({ left: 0, top: 0 })

const page = ref(1)
const boardImages = computed(() => props.board?.images ?? [])
const boardImageIds = computed(() => boardImages.value.map((img) => img.id))
const totalPages = computed(() => Math.max(1, Math.ceil(boardImages.value.length / PAGE_SIZE)))
const pageOffset = computed(() => (page.value - 1) * PAGE_SIZE)
const pagedImages = computed(() =>
  boardImages.value.slice(pageOffset.value, pageOffset.value + PAGE_SIZE),
)
const pageImageIds = computed(() => pagedImages.value.map((img) => img.id))
const allSelected = computed(
  () => boardImageIds.value.length > 0 && boardImageIds.value.every((id) => isSelected(id)),
)
const allPageSelected = computed(
  () => pageImageIds.value.length > 0 && pageImageIds.value.every((id) => isSelected(id)),
)

watch(
  () => props.board?.id,
  () => {
    pickerVisible.value = false
    exitSelection()
    page.value = 1
  },
)

onMounted(() => {
  // 切换灵感板后详情重新挂载，回到页面顶部更符合“打开新板”的预期
  window.scrollTo({ top: 0 })
  window.addEventListener('paste', onPaste)
})

// 路由整体被 keep-alive 缓存，离开页面时 onUnmounted 不会触发，需在 deactivated 时注销监听
onActivated(() => {
  window.addEventListener('paste', onPaste)
})

onDeactivated(() => {
  window.removeEventListener('paste', onPaste)
})

onUnmounted(() => {
  window.removeEventListener('paste', onPaste)
})

watch(totalPages, (value) => {
  if (page.value > value) page.value = value
})

function onPageChange(target: number) {
  page.value = target
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function onPaste(e: ClipboardEvent) {
  const board = props.board
  if (!board) return
  if (isEditableTarget(e.target) || editVisible.value || lightbox.visible) return

  const clipboardFiles = filesFromClipboard(e.clipboardData)
  if (!clipboardFiles.length) return

  const pasted = clipboardFiles.filter(isSupportedImage)
  if (!pasted.length) {
    e.preventDefault()
    message.warning('剪贴板里的图片格式不支持，仅支持 JPG / PNG / WebP')
    return
  }
  if (pasting.value) {
    message.info('正在上传，请等待完成后再粘贴')
    return
  }

  e.preventDefault()
  const stamp = clipTimestamp()
  const files = pasted.map((file, i) => normalizePastedImage(file, i, stamp))
  const boardId = board.id
  void uploadToBoard(boardId, board.name, files, () => {
    if (store.currentBoardId === boardId) page.value = 1
  })
}

function dragIdsFor(imageId: number): number[] {
  if (selectionMode.value && selectedIds.value.has(imageId)) {
    return Array.from(selectedIds.value)
  }
  return [imageId]
}

function openClassifyPicker(event: MouseEvent) {
  if (!selectedCount.value) return
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  pickerAnchor.value = { left: rect.left, top: rect.bottom + 4 }
  pickerImageIds.value = Array.from(selectedIds.value)
  pickerVisible.value = true
}

function onClassified(result: BatchAddResult) {
  pickerVisible.value = false
  if (result.added > 0) {
    message.success(
      `已添加 ${result.added} 张到「${result.board.name}」` +
        (result.skipped > 0 ? `，${result.skipped} 张已在该板` : ''),
    )
  } else {
    message.info(`所选图片均已在「${result.board.name}」中`)
  }
  exitSelection()
}

function requestBatchRemove() {
  if (!props.board || removing.value || !selectedCount.value) return
  const boardId = props.board.id
  const boardName = props.board.name
  const ids = Array.from(selectedIds.value)
  dialog.warning({
    title: '移出灵感板',
    content: `确定将选中的 ${ids.length} 张图片移出「${boardName}」吗？图片仍保留在图库和其他灵感板中。`,
    positiveText: '移出',
    negativeText: '取消',
    onPositiveClick: async () => {
      removing.value = true
      try {
        const result = await store.removeImages(boardId, ids)
        exitSelection()
        if (result.removed > 0) {
          message.success(`已移出 ${result.removed} 张图片`)
        } else {
          message.info('所选图片均不在该灵感板中')
        }
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : String(e)
        message.error('移出失败: ' + msg)
      } finally {
        removing.value = false
      }
    },
  })
}

function openPreview(index: number) {
  if (!boardImages.value.length) return
  const target = Math.min(pageOffset.value + index, boardImages.value.length - 1)
  lightbox.show(
    boardImages.value.map((img) => ({
      src: imageUrl(img.filePath),
      title: img.fileName || '(无文件名)',
    })),
    target,
  )
}

function openEdit() {
  if (!props.board) return
  editName.value = props.board.name
  editDescription.value = props.board.description || ''
  editVisible.value = true
}

async function saveEdit() {
  if (!props.board || saving.value) return
  const name = editName.value.trim()
  if (!name) return
  saving.value = true
  try {
    await store.update(props.board.id, {
      name,
      description: editDescription.value.trim(),
    })
    editVisible.value = false
    message.success('灵感板已更新')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    message.error('保存失败: ' + msg)
  } finally {
    saving.value = false
  }
}

function removeImage(imageId: number) {
  if (!props.board) return
  const boardId = props.board.id
  const image = props.board.images.find((img) => img.id === imageId)
  const label = image?.fileName ? `「${image.fileName}」` : '这张图片'
  dialog.warning({
    title: '从灵感板移除',
    content: `确定要移除${label}吗？图片不会从图库删除。`,
    positiveText: '移除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.removeImage(boardId, imageId)
        message.success('图片已移除')
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : String(e)
        message.error('移除失败: ' + msg)
      }
    },
  })
}
</script>

<style scoped>
.board-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.title-wrap {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

h2 {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -0.01em;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta {
  font-size: 12px;
  color: var(--text-4);
  font-variant-numeric: tabular-nums;
  flex: none;
}

.desc {
  font-size: 13px;
  color: var(--text-3);
  margin-top: -6px;
  white-space: pre-wrap;
}

.board-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 18px;
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-3);
}

.edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>

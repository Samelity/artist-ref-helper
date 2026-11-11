<template>
  <div class="board-sidebar">
    <section class="create">
      <p class="section-label">新建灵感板</p>
      <n-input v-model:value="name" placeholder="灵感板名称" @keydown.enter="create" />
      <n-input
        v-model:value="description"
        type="textarea"
        placeholder="描述（可选）"
        :autosize="{ minRows: 2, maxRows: 4 }"
      />
      <n-button type="primary" class="full" :disabled="!name.trim()" @click="create">创建</n-button>
    </section>

    <div class="list-head">
      <p class="section-label">全部灵感板</p>
      <span class="count">{{ store.boards.length }}</span>
    </div>

    <div class="board-list">
      <p v-if="!store.boards.length" class="empty-hint">暂无灵感板，先在上方创建一个</p>
      <button
        v-for="board in store.boards"
        :key="board.id"
        type="button"
        class="board-item"
        :class="{
          active: store.currentBoardId === board.id,
          'drop-target': dropBoardId === board.id,
        }"
        @click="store.select(board.id)"
        @dragover="onDragOver(board.id, $event)"
        @dragleave="onDragLeave(board.id, $event)"
        @drop.prevent="onDrop(board.id, $event)"
      >
        <span class="board-item__name">{{ board.name }}</span>
        <span class="board-item__desc">{{ board.description || '暂无描述' }}</span>
        <span class="board-item__meta">
          {{ board.imageCount }} 张 · {{ formatDate(board.createdAt) }}
        </span>
        <span
          class="board-item__del"
          role="button"
          title="删除灵感板"
          aria-label="删除灵感板"
          @click.stop="remove(board.id)"
        >
          <AppIcon name="trash" :size="14" />
        </span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { NInput, NButton, useMessage, useDialog } from 'naive-ui'
import AppIcon from '@/components/common/AppIcon.vue'
import { useBoardsStore } from '@/stores/boards'
import { formatDate } from '@/utils/format'
import { hasBoardDragData, readBoardDragIds } from '@/utils/boardDrag'

const store = useBoardsStore()
const message = useMessage()
const dialog = useDialog()

const name = ref('')
const description = ref('')
const dropBoardId = ref<number | null>(null)
const dropBusy = ref(false)

async function create() {
  const n = name.value.trim()
  if (!n) return
  try {
    await store.create({ name: n, description: description.value.trim() })
    name.value = ''
    description.value = ''
    message.success('灵感板创建成功')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    message.error('创建失败: ' + msg)
  }
}

function remove(id: number) {
  dialog.warning({
    title: '删除灵感板',
    content: '确定要删除这个灵感板吗？板内图片不会从图库删除。',
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.remove(id)
        message.success('灵感板已删除')
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : String(e)
        message.error('删除失败: ' + msg)
      }
    },
  })
}

function isDroppable(boardId: number) {
  return boardId !== store.currentBoardId
}

function onDragOver(boardId: number, event: DragEvent) {
  if (dropBusy.value || !isDroppable(boardId) || !hasBoardDragData(event)) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
  dropBoardId.value = boardId
}

function onDragLeave(boardId: number, event: DragEvent) {
  const el = event.currentTarget as HTMLElement | null
  const next = event.relatedTarget
  if (el && next instanceof Node && el.contains(next)) return
  if (dropBoardId.value === boardId) dropBoardId.value = null
}

async function onDrop(boardId: number, event: DragEvent) {
  dropBoardId.value = null
  if (dropBusy.value || !isDroppable(boardId)) return
  const imageIds = readBoardDragIds(event)
  if (!imageIds.length) return
  dropBusy.value = true
  try {
    const result = await store.addImages(boardId, imageIds)
    if (result.added > 0) {
      message.success(
        `已添加 ${result.added} 张到「${result.board.name}」` +
          (result.skipped > 0 ? `，${result.skipped} 张已在该板` : ''),
      )
    } else {
      message.info(`所选图片均已在「${result.board.name}」中`)
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    message.error('添加失败: ' + msg)
  } finally {
    dropBusy.value = false
  }
}
</script>

<style scoped>
.board-sidebar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
  min-height: 0;
}

.create {
  display: flex;
  flex-direction: column;
  flex: none;
  gap: 8px;
}

.section-label {
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--text-4);
}

.full {
  width: 100%;
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: none;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--border);
}

.count {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--text-3);
  background: var(--bg-hover);
  border-radius: var(--radius-full);
  padding: 1px 8px;
  font-variant-numeric: tabular-nums;
}

.board-list {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  gap: 6px;
  margin-top: 4px;
  padding-right: 2px;
  overflow-y: auto;
}

.empty-hint {
  padding: 14px 10px;
  font-size: 12.5px;
  color: var(--text-4);
  text-align: center;
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
}

.board-item {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  text-align: left;
  font-family: inherit;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--dur) var(--ease), border-color var(--dur) var(--ease);
}

.board-item:hover {
  background: var(--bg-hover);
}

.board-item.active {
  background: var(--brand-soft);
  border-color: rgba(79, 70, 229, 0.22);
}

.board-item.drop-target {
  background: var(--brand-soft);
  border-color: var(--brand);
  box-shadow: 0 0 0 2px var(--brand-ring);
}

.board-item.drop-target .board-item__name {
  color: var(--brand-pressed);
}

.board-item__name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-1);
  padding-right: 22px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.board-item.active .board-item__name {
  color: var(--brand-pressed);
}

.board-item__desc {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.board-item__meta {
  font-size: 11px;
  color: var(--text-4);
  font-variant-numeric: tabular-nums;
}

.board-item__del {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-xs);
  color: var(--text-4);
  opacity: 0;
  transition: opacity var(--dur) var(--ease), color var(--dur) var(--ease),
    background var(--dur) var(--ease);
}

.board-item:hover .board-item__del,
.board-item__del:focus-visible {
  opacity: 1;
}

.board-item__del:hover {
  color: var(--danger);
  background: var(--danger-soft);
}

@media (hover: none) {
  .board-item__del {
    opacity: 1;
  }
}

@media (max-width: 900px) {
  .board-sidebar {
    height: auto;
  }

  .board-list {
    flex: none;
    overflow: visible;
  }
}
</style>

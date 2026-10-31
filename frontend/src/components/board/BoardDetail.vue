<template>
  <div v-if="board" class="board-detail">
    <div class="detail-header">
      <div class="title-wrap">
        <h2>{{ board.name }}</h2>
        <span class="meta">{{ (board.images || []).length }} 张图片</span>
      </div>
      <n-button size="small" :theme-overrides="brandSoftButtonTheme" @click="openEdit">
        <template #icon>
          <AppIcon name="edit" :size="13" />
        </template>
        编辑
      </n-button>
    </div>
    <p v-if="board.description" class="desc">{{ board.description }}</p>
    <div v-if="board.images && board.images.length" class="board-grid">
      <BoardCard
        v-for="(img, i) in board.images"
        :key="img.id"
        :image="img"
        @preview="openPreview(i)"
        @remove="removeImage(img.id)"
      />
    </div>
    <EmptyState v-else text="灵感板暂无图片，搜索图片后点击 + 按钮添加" />

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
import { ref } from 'vue'
import { NButton, NInput, NModal, useDialog, useMessage } from 'naive-ui'
import BoardCard from './BoardCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { useBoardsStore } from '@/stores/boards'
import { useLightboxStore } from '@/stores/lightbox'
import { brandSoftButtonTheme } from '@/ui/buttonTheme'
import { imageUrl } from '@/utils/imageUrl'
import type { Board } from '@/types/board'

const props = defineProps<{ board: Board | null }>()
const store = useBoardsStore()
const lightbox = useLightboxStore()
const message = useMessage()
const dialog = useDialog()

const editVisible = ref(false)
const editName = ref('')
const editDescription = ref('')
const saving = ref(false)

function openPreview(index: number) {
  if (!props.board?.images) return
  lightbox.show(
    props.board.images.map((img) => ({
      src: imageUrl(img.filePath),
      title: img.fileName || '(无文件名)',
    })),
    index,
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
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 14px;
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

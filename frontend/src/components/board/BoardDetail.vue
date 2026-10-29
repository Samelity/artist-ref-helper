<template>
  <div v-if="board" class="board-detail">
    <div class="detail-header">
      <div class="title-wrap">
        <h2>{{ board.name }}</h2>
        <span class="meta">{{ (board.images || []).length }} 张图片</span>
      </div>
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
  </div>
  <EmptyState v-else text="加载失败" />
</template>

<script setup lang="ts">
import { useMessage } from 'naive-ui'
import BoardCard from './BoardCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useBoardsStore } from '@/stores/boards'
import { useLightboxStore } from '@/stores/lightbox'
import { imageUrl } from '@/utils/imageUrl'
import type { Board } from '@/types/board'

const props = defineProps<{ board: Board | null }>()
const store = useBoardsStore()
const lightbox = useLightboxStore()
const message = useMessage()

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

async function removeImage(imageId: number) {
  if (!props.board) return
  try {
    await store.removeImage(props.board.id, imageId)
    message.success('图片已移除')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    message.error('移除失败: ' + msg)
  }
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
}

.board-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 14px;
}
</style>

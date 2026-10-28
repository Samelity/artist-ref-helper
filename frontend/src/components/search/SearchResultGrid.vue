<template>
  <div class="result-grid" v-if="items.length">
    <ImageCard
      v-for="(item, i) in items"
      :key="item.id"
      :item="item"
      @preview="openPreview(i)"
      @add-to-board="(id, ev) => $emit('addToBoard', { imageId: id, event: ev })"
    />
  </div>
  <EmptyState v-else :text="emptyText || '暂无结果'" />
</template>

<script setup lang="ts">
import type { ImageItem } from '@/types/image'
import ImageCard from './ImageCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useLightboxStore } from '@/stores/lightbox'
import { imageUrl } from '@/utils/imageUrl'
import { formatScore } from '@/utils/format'

const props = defineProps<{
  items: ImageItem[]
  emptyText?: string
}>()

defineEmits<{
  addToBoard: [payload: { imageId: number, event: MouseEvent }]
}>()

const lightbox = useLightboxStore()

function openPreview(index: number) {
  lightbox.show(
    props.items.map((it) => ({
      src: imageUrl(it.filePath),
      title: it.fileName || '(无文件名)',
      subtitle: it.score != null ? `相似度 ${formatScore(it.score)}` : undefined,
    })),
    index,
  )
}
</script>

<style scoped>
.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 18px;
}
</style>

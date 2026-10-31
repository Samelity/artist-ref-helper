<template>
  <div class="board-card">
    <div class="media" title="点击预览" @click="$emit('preview')">
      <img :src="imageUrl(image.filePath)" :alt="image.fileName || ''" loading="lazy" />
      <button
        class="remove"
        type="button"
        title="从灵感板移除"
        aria-label="从灵感板移除"
        @click.stop="$emit('remove')"
      >
        <AppIcon name="close" :size="14" :stroke-width="2.2" />
      </button>
    </div>
    <p class="name" :title="image.fileName || ''">{{ image.fileName || '(无文件名)' }}</p>
  </div>
</template>

<script setup lang="ts">
import type { ImageItem } from '@/types/image'
import { imageUrl } from '@/utils/imageUrl'
import AppIcon from '@/components/common/AppIcon.vue'

defineProps<{ image: ImageItem }>()
defineEmits<{ remove: []; preview: [] }>()
</script>

<style scoped>
.board-card {
  position: relative;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  box-shadow: var(--shadow-xs);
  transition: box-shadow var(--dur) var(--ease), border-color var(--dur) var(--ease);
}

.board-card:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.media {
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--bg-hover);
  cursor: zoom-in;
}

.media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.name {
  padding: 7px 10px 8px;
  font-size: 12px;
  color: var(--text-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.remove {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  padding: 0;
  border: none;
  border-radius: 50%;
  color: #fff;
  background: rgba(17, 24, 39, 0.55);
  backdrop-filter: blur(4px);
  cursor: pointer;
  opacity: 0;
  transition: opacity var(--dur) var(--ease), background var(--dur) var(--ease);
}

.board-card:hover .remove,
.remove:focus-visible {
  opacity: 1;
}

.remove:hover {
  background: var(--danger);
}

@media (hover: none) {
  .remove {
    opacity: 1;
  }
}
</style>

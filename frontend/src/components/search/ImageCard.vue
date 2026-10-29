<template>
  <article class="image-card">
    <div class="media" title="点击预览" @click="$emit('preview')">
      <img :src="imageUrl(item.filePath)" :alt="item.fileName || ''" loading="lazy" />
      <span v-if="typeof item.score === 'number'" class="score">{{ formatScore(item.score) }}</span>
      <button
        class="add"
        type="button"
        title="添加到灵感板"
        aria-label="添加到灵感板"
        @click.stop="$emit('addToBoard', item.id, $event)"
      >
        <AppIcon name="plus" :size="16" :stroke-width="2.2" />
      </button>
    </div>
    <div class="info">
      <p class="name" :title="item.fileName || ''">{{ item.fileName || '(无文件名)' }}</p>
      <p class="meta">faiss #{{ item.faissId ?? '-' }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import type { ImageItem } from '@/types/image'
import { imageUrl } from '@/utils/imageUrl'
import { formatScore } from '@/utils/format'
import AppIcon from '@/components/common/AppIcon.vue'

defineProps<{ item: ImageItem }>()
defineEmits<{
  addToBoard: [imageId: number, event: MouseEvent]
  preview: []
}>()
</script>

<style scoped>
.image-card {
  position: relative;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: var(--shadow-xs);
  transition: transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.image-card:hover {
  transform: translateY(-2px);
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md);
}

.media {
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--bg-hover);
  overflow: hidden;
  cursor: zoom-in;
}

.media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s var(--ease);
}

.image-card:hover .media img {
  transform: scale(1.03);
}

.score {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 2px 8px;
  border-radius: var(--radius-full);
  background: rgba(17, 24, 39, 0.72);
  backdrop-filter: blur(4px);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.01em;
}

.add {
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  background: var(--brand);
  box-shadow: 0 4px 10px rgba(79, 70, 229, 0.35);
  cursor: pointer;
  opacity: 0;
  transform: translateY(4px);
  transition: opacity var(--dur) var(--ease), transform var(--dur) var(--ease),
    background var(--dur) var(--ease);
}

.image-card:hover .add,
.add:focus-visible {
  opacity: 1;
  transform: none;
}

.add:hover {
  background: var(--brand-hover);
}

.info {
  padding: 10px 12px 11px;
}

.name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--text-4);
  font-variant-numeric: tabular-nums;
}

@media (hover: none) {
  .add {
    opacity: 1;
    transform: none;
  }
}
</style>

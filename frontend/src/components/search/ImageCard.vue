<template>
  <article class="image-card" :class="{ 'is-selected': selectionMode && selected }">
    <div
      class="media"
      :class="{ 'is-selectable': selectionMode }"
      :role="selectionMode ? 'checkbox' : undefined"
      :aria-checked="selectionMode ? selected : undefined"
      :tabindex="selectionMode ? 0 : undefined"
      :title="mediaTitle"
      @click="onMediaClick"
      @keydown.enter.prevent="onToggleKey"
      @keydown.space.prevent="onToggleKey"
    >
      <img :src="imageUrl(item.filePath)" :alt="item.fileName || ''" loading="lazy" />
      <span v-if="typeof item.score === 'number'" class="score">{{ formatScore(item.score) }}</span>
      <button
        v-if="selectionMode"
        class="expand"
        type="button"
        title="预览大图"
        aria-label="预览大图"
        @click.stop="$emit('preview')"
      >
        <AppIcon name="expand" :size="13" />
      </button>
      <span v-if="selectionMode" class="check" :class="{ on: selected }" aria-hidden="true">
        <AppIcon v-if="selected" name="check" :size="14" :stroke-width="2.4" />
      </span>
      <button
        v-else
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
import { computed } from 'vue'
import type { ImageItem } from '@/types/image'
import { imageUrl } from '@/utils/imageUrl'
import { formatScore } from '@/utils/format'
import AppIcon from '@/components/common/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    item: ImageItem
    selectionMode?: boolean
    selected?: boolean
  }>(),
  { selectionMode: false, selected: false },
)

const emit = defineEmits<{
  addToBoard: [imageId: number, event: MouseEvent]
  preview: []
  toggleSelect: []
}>()

const mediaTitle = computed(() => {
  if (!props.selectionMode) return '点击预览'
  return props.selected ? '取消选中' : '选中图片'
})

function onMediaClick() {
  if (props.selectionMode) {
    emit('toggleSelect')
  } else {
    emit('preview')
  }
}

function onToggleKey() {
  if (props.selectionMode) emit('toggleSelect')
}
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

.image-card.is-selected {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-ring), var(--shadow-sm);
}

.media {
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--bg-hover);
  overflow: hidden;
  cursor: zoom-in;
}

.media.is-selectable {
  cursor: pointer;
}

.media:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: -2px;
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

.media.is-selectable .score {
  left: 38px;
}

.expand {
  position: absolute;
  top: 8px;
  left: 8px;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  padding: 0;
  border: none;
  border-radius: 50%;
  color: #fff;
  background: rgba(17, 24, 39, 0.55);
  backdrop-filter: blur(4px);
  cursor: pointer;
  transition: background var(--dur) var(--ease);
}

.expand:hover {
  background: var(--brand);
}

.check {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid var(--border-strong);
  backdrop-filter: blur(4px);
  transition: background var(--dur) var(--ease), border-color var(--dur) var(--ease);
}

.check.on {
  background: var(--brand);
  border-color: var(--brand);
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

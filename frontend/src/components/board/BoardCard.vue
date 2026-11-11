<template>
  <div
    class="board-card"
    :class="{ 'is-selected': selectionMode && selected, 'is-dragging': dragging }"
  >
    <div
      class="media"
      :class="{ 'is-selectable': selectionMode }"
      :role="selectionMode ? 'checkbox' : undefined"
      :aria-checked="selectionMode ? selected : undefined"
      :tabindex="selectionMode ? 0 : undefined"
      :title="mediaTitle"
      draggable="true"
      @click="onMediaClick"
      @keydown.enter.prevent="onToggleKey"
      @keydown.space.prevent="onToggleKey"
      @dragstart="onDragStart"
      @dragend="dragging = false"
    >
      <img
        :src="imageUrl(image.filePath)"
        :alt="image.fileName || ''"
        loading="lazy"
        draggable="false"
      />
      <button
        v-if="!selectionMode"
        class="remove"
        type="button"
        title="从灵感板移除"
        aria-label="从灵感板移除"
        @click.stop="$emit('remove')"
      >
        <AppIcon name="close" :size="14" :stroke-width="2.2" />
      </button>
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
    </div>
    <p class="name" :title="image.fileName || ''">{{ image.fileName || '(无文件名)' }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { ImageItem } from '@/types/image'
import { imageUrl } from '@/utils/imageUrl'
import { setBoardDragData } from '@/utils/boardDrag'
import AppIcon from '@/components/common/AppIcon.vue'

const props = withDefaults(
  defineProps<{
    image: ImageItem
    selectionMode?: boolean
    selected?: boolean
    dragIds?: number[]
  }>(),
  { selectionMode: false, selected: false, dragIds: undefined },
)

const emit = defineEmits<{
  remove: []
  preview: []
  toggleSelect: []
}>()

const dragging = ref(false)

const mediaTitle = computed(() => {
  if (props.selectionMode) return props.selected ? '取消选中' : '选中图片'
  return '点击预览，拖到左侧灵感板可归类'
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

function onDragStart(event: DragEvent) {
  const ids = props.dragIds?.length ? props.dragIds : [props.image.id]
  setBoardDragData(event, ids)
  dragging.value = true
}
</script>

<style scoped>
.board-card {
  position: relative;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  box-shadow: var(--shadow-xs);
  transition: box-shadow var(--dur) var(--ease), border-color var(--dur) var(--ease),
    opacity var(--dur) var(--ease);
}

.board-card:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-sm);
}

.board-card.is-selected {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-ring), var(--shadow-sm);
}

.board-card.is-dragging {
  opacity: 0.55;
}

.media {
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--bg-hover);
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

.check {
  position: absolute;
  top: 6px;
  right: 6px;
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

.expand {
  position: absolute;
  top: 6px;
  left: 6px;
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

@media (hover: none) {
  .remove {
    opacity: 1;
  }
}
</style>

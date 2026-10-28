<template>
  <div
    class="remote-card"
    :class="{ 'is-selected': selected }"
    role="checkbox"
    :aria-checked="selected"
    tabindex="0"
    @click="$emit('toggle')"
    @keydown.enter.prevent="$emit('toggle')"
    @keydown.space.prevent="$emit('toggle')"
  >
    <div class="media">
      <img v-if="photo.thumbnail" :src="photo.thumbnail" :alt="photo.alt || ''" loading="lazy" />
      <div v-else class="placeholder">无预览</div>
      <button
        class="expand"
        type="button"
        title="预览大图"
        aria-label="预览大图"
        @click.stop="$emit('preview')"
      >
        <AppIcon name="expand" :size="13" />
      </button>
      <span class="check" :class="{ on: selected }">
        <AppIcon v-if="selected" name="check" :size="14" :stroke-width="2.4" />
      </span>
    </div>
    <div class="info">
      <p class="photographer" :title="photo.photographer || ''">
        {{ photo.photographer || '未知作者' }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { RemotePhoto } from '@/types/remote'
import AppIcon from '@/components/common/AppIcon.vue'

defineProps<{
  photo: RemotePhoto
  selected: boolean
}>()

defineEmits<{ toggle: []; preview: [] }>()
</script>

<style scoped>
.remote-card {
  position: relative;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  box-shadow: var(--shadow-xs);
  transition: transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.remote-card:hover {
  transform: translateY(-2px);
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md);
}

.remote-card:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.remote-card.is-selected {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-ring), var(--shadow-sm);
}

.media {
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--bg-hover);
  overflow: hidden;
}

.media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s var(--ease);
}

.remote-card:hover .media img {
  transform: scale(1.03);
}

.placeholder {
  height: 100%;
  display: grid;
  place-items: center;
  color: var(--text-4);
  font-size: 12.5px;
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
  opacity: 0;
  transform: translateY(-2px);
  transition: opacity var(--dur) var(--ease), transform var(--dur) var(--ease),
    background var(--dur) var(--ease);
}

.remote-card:hover .expand,
.expand:focus-visible {
  opacity: 1;
  transform: none;
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

.info {
  padding: 8px 11px 9px;
}

.photographer {
  font-size: 12px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (hover: none) {
  .expand {
    opacity: 1;
    transform: none;
  }
}
</style>

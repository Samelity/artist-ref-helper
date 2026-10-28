<template>
  <Teleport to="body">
    <Transition name="lb">
      <div
        v-if="store.visible"
        class="lb"
        role="dialog"
        aria-modal="true"
        aria-label="图片预览"
        @click.self="store.close()"
      >
        <div class="lb-top">
          <span v-if="store.items.length > 1" class="lb-counter">
            {{ store.index + 1 }} / {{ store.items.length }}
          </span>
          <button class="lb-icon-btn" type="button" aria-label="关闭预览" @click="store.close()">
            <AppIcon name="close" :size="18" />
          </button>
        </div>

        <div class="lb-stage">
          <button
            v-if="store.items.length > 1"
            class="lb-icon-btn lb-nav prev"
            type="button"
            aria-label="上一张"
            @click.stop="store.prev()"
          >
            <AppIcon name="chevron-left" :size="22" />
          </button>

          <figure class="lb-figure" @click.stop>
            <img
              v-if="store.current"
              :key="store.current.src"
              :src="store.current.src"
              :alt="store.current.title || ''"
            />
            <figcaption v-if="store.current" class="lb-caption">
              <span class="lb-title" :title="store.current.title">{{ store.current.title }}</span>
              <span v-if="store.current.subtitle" class="lb-sub">{{ store.current.subtitle }}</span>
            </figcaption>
          </figure>

          <button
            v-if="store.items.length > 1"
            class="lb-icon-btn lb-nav next"
            type="button"
            aria-label="下一张"
            @click.stop="store.next()"
          >
            <AppIcon name="chevron-right" :size="22" />
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import { useLightboxStore } from '@/stores/lightbox'

const store = useLightboxStore()

function onKey(e: KeyboardEvent) {
  if (!store.visible) return
  if (e.key === 'Escape') {
    e.preventDefault()
    store.close()
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    store.next()
  } else if (e.key === 'ArrowLeft') {
    e.preventDefault()
    store.prev()
  }
}

let savedOverflow = ''
let savedPadding = ''

function lockScroll() {
  savedOverflow = document.body.style.overflow
  savedPadding = document.body.style.paddingRight
  const gap = window.innerWidth - document.documentElement.clientWidth
  document.body.style.overflow = 'hidden'
  if (gap > 0) document.body.style.paddingRight = `${gap}px`
}

function unlockScroll() {
  document.body.style.overflow = savedOverflow
  document.body.style.paddingRight = savedPadding
}

watch(
  () => store.visible,
  (open) => {
    if (open) lockScroll()
    else unlockScroll()
  },
)

onMounted(() => window.addEventListener('keydown', onKey))

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  unlockScroll()
})
</script>

<style scoped>
.lb {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 56px 72px 40px;
  background: rgba(9, 11, 18, 0.88);
  backdrop-filter: blur(6px);
  cursor: zoom-out;
}

.lb-top {
  position: absolute;
  top: 14px;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 0 18px;
}

.lb-counter {
  margin-right: auto;
  padding-left: 6px;
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.62);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}

.lb-icon-btn {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  padding: 0;
  color: rgba(255, 255, 255, 0.86);
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 50%;
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.lb-icon-btn:hover {
  background: rgba(255, 255, 255, 0.2);
  border-color: rgba(255, 255, 255, 0.28);
  color: #fff;
}

.lb-icon-btn:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

.lb-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.lb-figure {
  margin: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  max-width: min(1200px, 100%);
  max-height: 100%;
  cursor: default;
}

.lb-figure img {
  max-width: 100%;
  max-height: calc(100vh - 150px);
  object-fit: contain;
  border-radius: var(--radius-sm);
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.5);
  background: rgba(255, 255, 255, 0.04);
}

.lb-caption {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 100%;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.84);
}

.lb-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lb-sub {
  flex: none;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.55);
  font-variant-numeric: tabular-nums;
}

.lb-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
}

.lb-nav.prev {
  left: -56px;
}

.lb-nav.next {
  right: -56px;
}

.lb-enter-active,
.lb-leave-active {
  transition: opacity 0.18s var(--ease);
}

.lb-enter-active .lb-figure,
.lb-leave-active .lb-figure {
  transition: transform 0.18s var(--ease);
}

.lb-enter-from,
.lb-leave-to {
  opacity: 0;
}

.lb-enter-from .lb-figure,
.lb-leave-to .lb-figure {
  transform: scale(0.97);
}

@media (max-width: 900px) {
  .lb {
    padding: 56px 14px 40px;
  }

  .lb-nav.prev {
    left: 4px;
  }

  .lb-nav.next {
    right: 4px;
  }

  .lb-figure img {
    max-height: calc(100vh - 170px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .lb-enter-active,
  .lb-leave-active,
  .lb-enter-active .lb-figure,
  .lb-leave-active .lb-figure {
    transition: none;
  }
}
</style>

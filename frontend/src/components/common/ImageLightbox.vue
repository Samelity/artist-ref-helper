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

          <figure class="lb-figure">
            <div
              ref="viewportRef"
              class="lb-viewport"
              :class="{
                'is-checker': checker,
                'can-pan': canPan,
                'is-dragging': dragging,
                'no-anim': !animating,
              }"
              @wheel.prevent="onWheel"
              @pointerdown="onPointerDown"
              @pointermove="onPointerMove"
              @pointerup="endDrag"
              @pointercancel="endDrag"
              @lostpointercapture="endDrag"
              @click="onViewportClick"
              @dblclick="onDblClick"
            >
              <div class="lb-canvas" :style="canvasStyle">
                <img
                  v-if="store.current"
                  :key="store.current.src"
                  :src="store.current.src"
                  :alt="store.current.title || ''"
                  draggable="false"
                  @load="onImgLoad"
                  @dragstart.prevent
                />
              </div>
            </div>

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

        <div class="lb-toolbar" role="toolbar" aria-label="预览工具" @click.stop>
          <button
            class="lb-icon-btn tool"
            :class="{ 'is-on': checker }"
            type="button"
            aria-label="切换格子背景"
            :aria-pressed="checker"
            title="格子背景"
            @click="checker = !checker"
          >
            <AppIcon name="grid" :size="16" />
          </button>

          <span class="lb-divider" />

          <button
            class="lb-icon-btn tool"
            type="button"
            aria-label="向左旋转 90 度"
            title="向左旋转 (Shift + R)"
            @click="rotateBy(-1)"
          >
            <AppIcon name="rotate-left" :size="16" />
          </button>
          <button
            class="lb-icon-btn tool"
            type="button"
            aria-label="向右旋转 90 度"
            title="向右旋转 (R)"
            @click="rotateBy(1)"
          >
            <AppIcon name="rotate-right" :size="16" />
          </button>

          <span class="lb-divider" />

          <button
            class="lb-icon-btn tool"
            type="button"
            aria-label="缩小"
            title="缩小 (-)"
            :disabled="atMin"
            @click="zoomStep(1 / 1.25)"
          >
            <AppIcon name="zoom-out" :size="16" />
          </button>
          <span class="lb-percent">{{ percentLabel }}</span>
          <button
            class="lb-icon-btn tool"
            type="button"
            aria-label="放大"
            title="放大 (+)"
            :disabled="atMax"
            @click="zoomStep(1.25)"
          >
            <AppIcon name="zoom-in" :size="16" />
          </button>

          <span class="lb-divider" />

          <button
            class="lb-icon-btn tool"
            type="button"
            aria-label="适应窗口"
            title="适应窗口 (0)"
            :disabled="isFit"
            @click="applyFit()"
          >
            <AppIcon name="expand" :size="16" />
          </button>
          <button
            class="lb-text-btn"
            type="button"
            aria-label="原始尺寸"
            title="原始尺寸 (双击图片切换)"
            :disabled="isActual"
            @click="zoomToActual"
          >
            1:1
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import { useLightboxStore } from '@/stores/lightbox'

const MIN_SCALE = 0.25
const MAX_SCALE = 8
const EPS = 0.0005

const store = useLightboxStore()

const viewportRef = ref<HTMLElement | null>(null)
const naturalW = ref(0)
const naturalH = ref(0)
const viewportW = ref(0)
const viewportH = ref(0)
const scale = ref(1)
const rotation = ref(0)
const tx = ref(0)
const ty = ref(0)
const checker = ref(false)
const animating = ref(false)
const dragging = ref(false)

let dragOrigin = { x: 0, y: 0, tx: 0, ty: 0 }
let dragMoved = false

const swapped = computed(() => Math.abs(rotation.value % 180) === 90)

const baseScale = computed(() => {
  if (!naturalW.value || !naturalH.value || !viewportW.value || !viewportH.value) return 1
  const w = swapped.value ? naturalH.value : naturalW.value
  const h = swapped.value ? naturalW.value : naturalH.value
  return Math.min(1, viewportW.value / w, viewportH.value / h)
})

const minScale = computed(() => Math.min(MIN_SCALE, baseScale.value))
const isFit = computed(() => Math.abs(scale.value - baseScale.value) < EPS)
const isActual = computed(() => Math.abs(scale.value - 1) < EPS)
const atMin = computed(() => scale.value <= minScale.value + EPS)
const atMax = computed(() => scale.value >= MAX_SCALE - EPS)
const percentLabel = computed(() => `${Math.round(scale.value * 100)}%`)

const panLimits = computed(() => {
  const w = (swapped.value ? naturalH.value : naturalW.value) * scale.value
  const h = (swapped.value ? naturalW.value : naturalH.value) * scale.value
  return {
    x: Math.max(0, (w - viewportW.value) / 2),
    y: Math.max(0, (h - viewportH.value) / 2),
  }
})

const canPan = computed(() => panLimits.value.x > 0 || panLimits.value.y > 0)

const canvasStyle = computed(() => ({
  width: `${naturalW.value}px`,
  height: `${naturalH.value}px`,
  transform: `translate(-50%, -50%) translate(${tx.value}px, ${ty.value}px) rotate(${rotation.value}deg) scale(${scale.value})`,
}))

function clamp(v: number, lo: number, hi: number) {
  return Math.min(hi, Math.max(lo, v))
}

function clampPan() {
  const lim = panLimits.value
  tx.value = clamp(tx.value, -lim.x, lim.x)
  ty.value = clamp(ty.value, -lim.y, lim.y)
}

function applyZoom(next: number, cx = 0, cy = 0) {
  const target = clamp(next, minScale.value, MAX_SCALE)
  const prev = scale.value
  if (Math.abs(target - prev) < 1e-6) return
  const k = target / prev
  tx.value = cx - k * (cx - tx.value)
  ty.value = cy - k * (cy - ty.value)
  scale.value = target
  clampPan()
}

function zoomStep(factor: number) {
  animating.value = true
  applyZoom(scale.value * factor)
}

function zoomToActual() {
  animating.value = true
  applyZoom(1)
}

function applyFit(animate = true) {
  animating.value = animate
  scale.value = baseScale.value
  tx.value = 0
  ty.value = 0
}

function rotateBy(dir: 1 | -1) {
  animating.value = true
  const wasFit = isFit.value
  rotation.value += dir * 90
  tx.value = 0
  ty.value = 0
  if (wasFit) scale.value = baseScale.value
  clampPan()
}

function measureViewport() {
  const el = viewportRef.value
  if (!el) return
  const wasFit = isFit.value
  viewportW.value = el.clientWidth
  viewportH.value = el.clientHeight
  if (wasFit) {
    scale.value = baseScale.value
    tx.value = 0
    ty.value = 0
  } else {
    clampPan()
  }
}

function onWheel(e: WheelEvent) {
  const el = viewportRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const cx = e.clientX - rect.left - rect.width / 2
  const cy = e.clientY - rect.top - rect.height / 2
  const delta = e.deltaMode === 1 ? e.deltaY * 16 : e.deltaY
  animating.value = false
  applyZoom(scale.value * clamp(Math.exp(-delta * 0.0015), 0.8, 1.25), cx, cy)
}

function onPointerDown(e: PointerEvent) {
  if (e.button !== 0 || !canPan.value) return
  dragging.value = true
  animating.value = false
  dragMoved = false
  dragOrigin = { x: e.clientX, y: e.clientY, tx: tx.value, ty: ty.value }
  viewportRef.value?.setPointerCapture(e.pointerId)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return
  const dx = e.clientX - dragOrigin.x
  const dy = e.clientY - dragOrigin.y
  if (Math.abs(dx) + Math.abs(dy) > 3) dragMoved = true
  tx.value = dragOrigin.tx + dx
  ty.value = dragOrigin.ty + dy
  clampPan()
}

function endDrag(e: PointerEvent) {
  if (!dragging.value) return
  dragging.value = false
  const el = viewportRef.value
  if (el?.hasPointerCapture(e.pointerId)) el.releasePointerCapture(e.pointerId)
}

function onViewportClick(e: MouseEvent) {
  if (dragMoved) {
    dragMoved = false
    return
  }
  if (e.target !== viewportRef.value) return
  store.close()
}

function onDblClick(e: MouseEvent) {
  const el = viewportRef.value
  if (!el || !naturalW.value) return
  animating.value = true
  if (isFit.value) {
    const rect = el.getBoundingClientRect()
    applyZoom(1, e.clientX - rect.left - rect.width / 2, e.clientY - rect.top - rect.height / 2)
  } else {
    applyFit()
  }
}

function onImgLoad(e: Event) {
  const img = e.target as HTMLImageElement
  naturalW.value = img.naturalWidth || 0
  naturalH.value = img.naturalHeight || 0
  measureViewport()
  applyFit(false)
}

watch(
  () => store.current?.src,
  () => {
    naturalW.value = 0
    naturalH.value = 0
    rotation.value = 0
    tx.value = 0
    ty.value = 0
    scale.value = 1
    animating.value = false
  },
)

function onKey(e: KeyboardEvent) {
  if (!store.visible) return
  if (e.ctrlKey || e.metaKey || e.altKey) return
  if (e.key === 'Escape') {
    e.preventDefault()
    store.close()
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    store.next()
  } else if (e.key === 'ArrowLeft') {
    e.preventDefault()
    store.prev()
  } else if (e.key === '+' || e.key === '=') {
    e.preventDefault()
    zoomStep(1.25)
  } else if (e.key === '-' || e.key === '_') {
    e.preventDefault()
    zoomStep(1 / 1.25)
  } else if (e.key === '0') {
    e.preventDefault()
    applyFit()
  } else if (e.key === 'r') {
    e.preventDefault()
    rotateBy(1)
  } else if (e.key === 'R') {
    e.preventDefault()
    rotateBy(-1)
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

let ro: ResizeObserver | null = null

watch(viewportRef, (el, prev) => {
  if (!ro) return
  if (prev) ro.unobserve(prev)
  if (el) {
    ro.observe(el)
    measureViewport()
  }
})

watch(
  () => store.visible,
  (open) => {
    if (open) lockScroll()
    else unlockScroll()
  },
)

onMounted(() => {
  ro = new ResizeObserver(() => measureViewport())
  window.addEventListener('keydown', onKey)
})

onBeforeUnmount(() => {
  ro?.disconnect()
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
  padding: 0;
  background: rgba(9, 11, 18, 0.88);
  backdrop-filter: blur(6px);
  cursor: zoom-out;
}

.lb-top {
  position: absolute;
  top: 14px;
  left: 0;
  right: 0;
  z-index: 4;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 0 18px;
}

.lb-counter {
  margin-right: auto;
  padding: 3px 10px 3px 12px;
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.86);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
  background: rgba(18, 21, 30, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 999px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(8px);
}

.lb-icon-btn {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  padding: 0;
  color: rgba(255, 255, 255, 0.92);
  background: rgba(18, 21, 30, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(8px);
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.lb-icon-btn:not(:disabled):hover {
  background: rgba(18, 21, 30, 0.8);
  border-color: rgba(255, 255, 255, 0.36);
  color: #fff;
}

.lb-icon-btn:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

.lb-icon-btn:disabled {
  opacity: 0.32;
  cursor: default;
}

.lb-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  min-height: 0;
}

.lb-figure {
  position: relative;
  margin: 0;
  width: 100%;
  height: 100%;
  cursor: default;
}

.lb-viewport {
  position: absolute;
  inset: 0;
  overflow: hidden;
  touch-action: none;
  user-select: none;
  cursor: default;
}

.lb-viewport.is-checker::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image: conic-gradient(
    rgba(255, 255, 255, 0.055) 0 25%,
    transparent 0 50%,
    rgba(255, 255, 255, 0.055) 0 75%,
    transparent 0
  );
  background-size: 24px 24px;
}

.lb-viewport.can-pan {
  cursor: grab;
}

.lb-viewport.can-pan.is-dragging {
  cursor: grabbing;
}

.lb-canvas {
  position: absolute;
  top: 50%;
  left: 50%;
  will-change: transform;
  transition: transform 0.22s cubic-bezier(0.22, 0.61, 0.36, 1);
}

.lb-viewport.no-anim .lb-canvas {
  transition: none;
}

.lb-canvas img {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: var(--radius-sm);
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.5);
}

.lb-caption {
  position: absolute;
  left: 50%;
  bottom: 68px;
  z-index: 2;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: min(82%, 720px);
  padding: 4px 14px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.94);
  pointer-events: none;
  text-shadow:
    0 0 6px rgba(0, 0, 0, 0.8),
    1px 0 1px rgba(0, 0, 0, 0.7),
    -1px 0 1px rgba(0, 0, 0, 0.7),
    0 1px 1px rgba(0, 0, 0, 0.7),
    0 -1px 1px rgba(0, 0, 0, 0.7);
}

.lb-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lb-sub {
  flex: none;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.82);
  font-variant-numeric: tabular-nums;
}

.lb-nav {
  position: absolute;
  top: 50%;
  z-index: 3;
  transform: translateY(-50%);
  width: 44px;
  height: 44px;
}

.lb-nav.prev {
  left: 16px;
}

.lb-nav.next {
  right: 16px;
}

.lb-toolbar {
  position: absolute;
  left: 50%;
  bottom: 16px;
  z-index: 4;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 5px 8px;
  background: rgba(18, 21, 30, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(12px);
}

.lb-toolbar .lb-icon-btn.tool {
  width: 32px;
  height: 32px;
  background: transparent;
  border-color: transparent;
  box-shadow: none;
  backdrop-filter: none;
}

.lb-toolbar .lb-icon-btn.tool:not(:disabled):hover {
  background: rgba(255, 255, 255, 0.14);
  border-color: transparent;
}

.lb-toolbar .lb-icon-btn.tool.is-on {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}.lb-percent {
  min-width: 46px;
  text-align: center;
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.78);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.lb-divider {
  width: 1px;
  height: 18px;
  margin: 0 5px;
  background: rgba(255, 255, 255, 0.14);
}

.lb-text-btn {
  min-width: 36px;
  height: 32px;
  padding: 0 8px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: rgba(255, 255, 255, 0.86);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 999px;
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease);
}

.lb-text-btn:not(:disabled):hover {
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
}

.lb-text-btn:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
}

.lb-text-btn:disabled {
  opacity: 0.32;
  cursor: default;
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
  .lb-nav.prev {
    left: 10px;
  }

  .lb-nav.next {
    right: 10px;
  }

  .lb-caption {
    bottom: 64px;
  }
}

@media (max-width: 600px) {
  .lb-toolbar {
    gap: 0;
    padding: 4px 6px;
  }

  .lb-toolbar .lb-icon-btn.tool {
    width: 30px;
    height: 30px;
  }

  .lb-percent {
    min-width: 40px;
    font-size: 12px;
  }

  .lb-divider {
    margin: 0 3px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .lb-enter-active,
  .lb-leave-active,
  .lb-enter-active .lb-figure,
  .lb-leave-active .lb-figure,
  .lb-canvas {
    transition: none;
  }
}
</style>

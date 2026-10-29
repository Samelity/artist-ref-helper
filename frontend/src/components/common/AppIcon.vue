<template>
  <svg
    class="app-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-for="(shape, i) in shapes" :key="i">
      <path v-if="shape.t === 'p'" :d="shape.d" />
      <rect v-else-if="shape.t === 'r'" :x="shape.x" :y="shape.y" :width="shape.w" :height="shape.h" :rx="shape.rx ?? 0" />
      <circle v-else :cx="shape.cx" :cy="shape.cy" :r="shape.r" />
    </template>
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type Shape =
  | { t: 'p'; d: string }
  | { t: 'r'; x: number; y: number; w: number; h: number; rx?: number }
  | { t: 'c'; cx: number; cy: number; r: number }

const props = withDefaults(
  defineProps<{
    name: string
    size?: number | string
    strokeWidth?: number
  }>(),
  { size: 18, strokeWidth: 1.8 },
)

const icons: Record<string, Shape[]> = {
  search: [
    { t: 'c', cx: 11, cy: 11, r: 7 },
    { t: 'p', d: 'm16.2 16.2 4.3 4.3' },
  ],
  text: [
    { t: 'p', d: 'M4 6h16' },
    { t: 'p', d: 'M4 12h11' },
    { t: 'p', d: 'M4 18h7' },
  ],
  image: [
    { t: 'r', x: 3.5, y: 3.5, w: 17, h: 17, rx: 3 },
    { t: 'p', d: 'm4.5 16.5 4.4-4.4a1.6 1.6 0 0 1 2.2 0l5.4 5.4' },
    { t: 'c', cx: 15.2, cy: 9.3, r: 1.4 },
  ],
  globe: [
    { t: 'c', cx: 12, cy: 12, r: 8.5 },
    { t: 'p', d: 'M3.6 9h16.8' },
    { t: 'p', d: 'M3.6 15h16.8' },
    {
      t: 'p',
      d: 'M12 3.5c2.3 2.4 3.4 5.3 3.4 8.5s-1.1 6.1-3.4 8.5c-2.3-2.4-3.4-5.3-3.4-8.5S9.7 5.9 12 3.5Z',
    },
  ],
  boards: [
    { t: 'r', x: 3.5, y: 3.5, w: 7, h: 7, rx: 2 },
    { t: 'r', x: 13.5, y: 3.5, w: 7, h: 7, rx: 2 },
    { t: 'r', x: 3.5, y: 13.5, w: 7, h: 7, rx: 2 },
    { t: 'r', x: 13.5, y: 13.5, w: 7, h: 7, rx: 2 },
  ],
  gallery: [
    { t: 'r', x: 3.5, y: 3.5, w: 17, h: 17, rx: 3 },
    { t: 'p', d: 'M3.5 9.5h17' },
    { t: 'p', d: 'M9.5 9.5v11' },
  ],
  refresh: [
    { t: 'p', d: 'M20.5 12a8.5 8.5 0 1 1-2.5-6.05' },
    { t: 'p', d: 'M20.5 3.5V9h-5.5' },
  ],
  plus: [
    { t: 'p', d: 'M12 5v14' },
    { t: 'p', d: 'M5 12h14' },
  ],
  close: [
    { t: 'p', d: 'm6.5 6.5 11 11' },
    { t: 'p', d: 'm17.5 6.5-11 11' },
  ],
  trash: [
    { t: 'p', d: 'M4 7h16' },
    { t: 'p', d: 'M10 11v6' },
    { t: 'p', d: 'M14 11v6' },
    { t: 'p', d: 'M6.5 7l.9 12a2 2 0 0 0 2 1.9h5.2a2 2 0 0 0 2-1.9l.9-12' },
    { t: 'p', d: 'M9 7V5.2A1.7 1.7 0 0 1 10.7 3.5h2.6A1.7 1.7 0 0 1 15 5.2V7' },
  ],
  download: [
    { t: 'p', d: 'M12 4v11' },
    { t: 'p', d: 'm7.5 10.5 4.5 4.5 4.5-4.5' },
    { t: 'p', d: 'M5 20h14' },
  ],
  upload: [
    { t: 'p', d: 'M12 20V9' },
    { t: 'p', d: 'm7.5 13.5 4.5-4.5 4.5 4.5' },
    { t: 'p', d: 'M5 4h14' },
  ],
  sparkles: [
    {
      t: 'p',
      d: 'm12 3.5 1.8 4.7 4.7 1.8-4.7 1.8L12 16.5l-1.8-4.7L5.5 10l4.7-1.8L12 3.5Z',
    },
    { t: 'p', d: 'M18.5 15.5l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8.8-2Z' },
  ],
  check: [{ t: 'p', d: 'm5 12.5 4.5 4.5L19 7.5' }],
  'chevron-left': [{ t: 'p', d: 'm14.5 5.5-6.5 6.5 6.5 6.5' }],
  'chevron-right': [{ t: 'p', d: 'm9.5 5.5 6.5 6.5-6.5 6.5' }],
  expand: [
    { t: 'p', d: 'M9.5 4H4v5.5' },
    { t: 'p', d: 'M14.5 20H20v-5.5' },
    { t: 'p', d: 'M20 9.5V4h-5.5' },
    { t: 'p', d: 'M4 14.5V20h5.5' },
  ],
}

const shapes = computed<Shape[]>(() => icons[props.name] ?? [])
</script>

<style scoped>
.app-icon {
  flex: none;
  display: block;
}
</style>

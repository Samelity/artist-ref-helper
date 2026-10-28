import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface PreviewItem {
  src: string
  title?: string
  subtitle?: string
}

export const useLightboxStore = defineStore('lightbox', () => {
  const items = ref<PreviewItem[]>([])
  const index = ref(0)

  const visible = computed(() => items.value.length > 0)
  const current = computed<PreviewItem | null>(() => items.value[index.value] ?? null)

  function show(list: PreviewItem[], start = 0) {
    const before = list.slice(0, start).filter((i) => i.src).length
    const usable = list.filter((i) => i.src)
    if (!usable.length) return
    items.value = usable
    index.value = Math.min(before, usable.length - 1)
  }

  function close() {
    items.value = []
    index.value = 0
  }

  function next() {
    if (items.value.length > 1) index.value = (index.value + 1) % items.value.length
  }

  function prev() {
    if (items.value.length > 1) {
      index.value = (index.value - 1 + items.value.length) % items.value.length
    }
  }

  return { items, index, visible, current, show, close, next, prev }
})

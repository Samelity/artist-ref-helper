import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RemotePhoto, RemoteSource } from '@/types/remote'

export const useRemoteSelectionStore = defineStore('remoteSelection', () => {
  const selected = ref<Map<string | number, RemotePhoto>>(new Map())
  const source = ref<RemoteSource>('pixabay')

  const count = computed(() => selected.value.size)
  const hasSelection = computed(() => selected.value.size > 0)

  function toggle(photo: RemotePhoto) {
    const next = new Map(selected.value)
    if (next.has(photo.id)) {
      next.delete(photo.id)
    } else {
      next.set(photo.id, photo)
    }
    selected.value = next
  }

  function isSelected(id: string | number): boolean {
    return selected.value.has(id)
  }

  function clear() {
    selected.value = new Map()
  }

  function setSource(s: RemoteSource) {
    source.value = s
  }

  return { selected, source, count, hasSelection, toggle, isSelected, clear, setSource }
})

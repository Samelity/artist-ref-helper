import { computed, ref } from 'vue'

export function useImageSelection() {
  const mode = ref(false)
  const ids = ref<Set<number>>(new Set())

  const count = computed(() => ids.value.size)

  function enter() {
    mode.value = true
  }

  function exit() {
    mode.value = false
    ids.value = new Set()
  }

  function toggle(id: number) {
    const next = new Set(ids.value)
    if (next.has(id)) {
      next.delete(id)
    } else {
      next.add(id)
    }
    ids.value = next
  }

  function selectAll(list: number[]) {
    const next = new Set(ids.value)
    for (const id of list) next.add(id)
    ids.value = next
  }

  function clear() {
    ids.value = new Set()
  }

  function isSelected(id: number) {
    return ids.value.has(id)
  }

  return { mode, ids, count, enter, exit, toggle, selectAll, clear, isSelected }
}

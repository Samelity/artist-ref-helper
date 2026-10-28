<template>
  <Teleport to="body">
    <div v-if="visible" ref="pickerRef" class="board-picker" :style="posStyle">
      <p class="picker-title">添加到灵感板</p>
      <div v-if="loading" class="picker-row muted">加载中…</div>
      <div v-else-if="!store.boards.length" class="picker-row muted">
        暂无灵感板，先创建再添加
      </div>
      <template v-else>
        <button
          v-for="board in store.boards"
          :key="board.id"
          type="button"
          class="picker-row picker-item"
          @click="add(board.id)"
        >
          <span class="item-name">{{ board.name }}</span>
          <span class="item-count">{{ (board.images || []).length }}</span>
        </button>
      </template>
      <button type="button" class="picker-new" @click="goToBoards">
        <AppIcon name="plus" :size="14" :stroke-width="2.2" />
        新建灵感板
      </button>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import AppIcon from '@/components/common/AppIcon.vue'
import { useBoardsStore } from '@/stores/boards'

const props = defineProps<{
  imageId: number
  anchor: { left: number; top: number }
}>()

const emit = defineEmits<{
  close: []
  added: []
}>()

const router = useRouter()
const store = useBoardsStore()
const message = useMessage()

const visible = ref(true)
const loading = ref(false)
const pickerRef = ref<HTMLElement | null>(null)
const pos = ref({ left: props.anchor.left, top: props.anchor.top })

const posStyle = computed(() => ({ left: `${pos.value.left}px`, top: `${pos.value.top}px` }))

onMounted(async () => {
  if (!store.boards.length) {
    loading.value = true
    try {
      await store.load()
    } catch {
      store.boards = []
    } finally {
      loading.value = false
    }
  }

  await nextTick()
  const el = pickerRef.value
  if (el) {
    const left = Math.min(
      Math.max(8, props.anchor.left),
      window.innerWidth - el.offsetWidth - 8,
    )
    const top = Math.min(props.anchor.top, window.innerHeight - el.offsetHeight - 8)
    pos.value = { left, top }
  }

  document.addEventListener('click', onClickOutside, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onClickOutside, true)
})

function onClickOutside(e: MouseEvent) {
  if (pickerRef.value && !pickerRef.value.contains(e.target as Node)) {
    emit('close')
  }
}

async function add(boardId: number) {
  try {
    await store.addImage(boardId, props.imageId)
    emit('added')
    emit('close')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    message.error('添加失败：' + msg)
  }
}

function goToBoards() {
  emit('close')
  router.push('/boards')
}
</script>

<style scoped>
.board-picker {
  position: fixed;
  z-index: 100;
  width: 232px;
  padding: 6px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
  animation: pop 0.14s var(--ease);
}

@keyframes pop {
  from {
    opacity: 0;
    transform: translateY(-4px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.picker-title {
  padding: 6px 10px 8px;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text-4);
}

.picker-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 7px 10px;
  font-size: 13px;
  font-family: inherit;
  text-align: left;
  color: var(--text-2);
  background: transparent;
  border: none;
  border-radius: var(--radius-xs);
}

.muted {
  color: var(--text-4);
  font-size: 12.5px;
}

.picker-item {
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease);
}

.picker-item:hover {
  background: var(--brand-soft);
  color: var(--brand-pressed);
}

.item-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-count {
  flex: none;
  font-size: 11px;
  color: var(--text-4);
  font-variant-numeric: tabular-nums;
}

.picker-new {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  margin-top: 4px;
  padding: 8px 10px;
  font-family: inherit;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--brand);
  background: transparent;
  border: none;
  border-top: 1px solid var(--border);
  border-radius: 0 0 var(--radius-xs) var(--radius-xs);
  cursor: pointer;
  transition: background var(--dur) var(--ease);
}

.picker-new:hover {
  background: var(--brand-soft);
}

@media (prefers-reduced-motion: reduce) {
  .board-picker {
    animation: none;
  }
}
</style>

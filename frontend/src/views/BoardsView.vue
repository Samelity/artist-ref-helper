<template>
  <section class="view boards-view">
    <aside class="board-side">
      <BoardSidebar />
    </aside>
    <div class="board-main">
      <div class="board-main-inner">
        <PageHeader
          title="灵感板"
          description="把检索到的参考图按项目或风格归类整理；板内图片可直接拖到左侧其他灵感板完成二次归类。"
        />
        <div class="surface surface-pad board-panel">
          <BoardDetail v-if="store.currentBoard" :board="store.currentBoard" />
          <EmptyState v-else :text="detailEmptyText" />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/layout/PageHeader.vue'
import BoardSidebar from '@/components/board/BoardSidebar.vue'
import BoardDetail from '@/components/board/BoardDetail.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useBoardsStore } from '@/stores/boards'

defineOptions({ name: 'BoardsView' })

const store = useBoardsStore()
const route = useRoute()
const router = useRouter()

const detailEmptyText = computed(() => {
  if (store.detailLoading) return '灵感板加载中…'
  if (store.currentBoardId != null) return '灵感板加载失败，请重新选择'
  return '选择左侧灵感板查看详情，或创建一个新的灵感板'
})

onMounted(async () => {
  await store.load()
  const id = route.params.boardId
  if (id) {
    await store.select(Number(id))
  } else if (store.currentBoardId != null) {
    await store.select(store.currentBoardId)
  }
})

let firstActivation = true
onActivated(async () => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  await store.load()
  await store.refreshCurrent()
})

watch(
  () => route.params.boardId,
  (id) => {
    if (id) void store.select(Number(id))
  },
)

watch(
  () => store.currentBoardId,
  (id) => {
    if (id == null) {
      if (route.name === 'board-detail') router.replace('/boards')
    } else {
      if (route.params.boardId !== String(id)) {
        router.replace(`/boards/${id}`)
      }
    }
  },
)
</script>

<style scoped>
.boards-view {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  align-items: start;
  gap: 0;
  min-height: 100vh;
  min-height: 100dvh;
}

.board-side {
  position: sticky;
  top: 0;
  height: 100vh;
  height: 100dvh;
  padding: 20px 16px;
  overflow: hidden;
  background: var(--bg-subtle);
  border-right: 1px solid var(--border);
}

.board-main {
  min-width: 0;
  padding: 32px 36px 64px;
}

.board-main-inner {
  display: flex;
  flex-direction: column;
  gap: var(--gap-lg);
  max-width: var(--content-max);
  margin: 0 auto;
}

@media (max-width: 960px) {
  .board-main {
    padding: 22px 18px 48px;
  }
}

@media (max-width: 900px) {
  .boards-view {
    display: block;
    min-height: 0;
  }

  .board-side {
    position: static;
    height: auto;
    padding: 18px 16px;
    border-right: none;
    border-bottom: 1px solid var(--border);
  }

  .board-main {
    padding: 20px 18px 48px;
  }
}
</style>

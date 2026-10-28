<template>
  <section class="view">
    <PageHeader
      title="灵感板"
      description="把检索到的参考图按项目或风格归类整理，随时打包导出给美术。"
    />

    <div class="board-layout">
      <div class="surface surface-pad board-side">
        <BoardSidebar />
      </div>
      <div class="surface surface-pad board-main">
        <BoardDetail v-if="store.currentBoard" :board="store.currentBoard" />
        <EmptyState v-else text="选择左侧灵感板查看详情，或创建一个新的灵感板" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onActivated, onMounted, watch } from 'vue'
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

onMounted(async () => {
  await store.load()
  const id = route.params.boardId
  if (id) {
    store.select(Number(id))
  }
})

let firstActivation = true
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  store.load()
})

watch(
  () => route.params.boardId,
  (id) => {
    if (id) store.select(Number(id))
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
.board-layout {
  display: grid;
  grid-template-columns: minmax(240px, 300px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}

.board-side {
  position: sticky;
  top: 24px;
}

.board-main {
  min-height: 420px;
}

@media (max-width: 900px) {
  .board-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .board-side {
    position: static;
  }
}
</style>

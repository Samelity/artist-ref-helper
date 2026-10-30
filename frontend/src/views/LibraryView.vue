<template>
  <section class="view">
    <PageHeader
      title="图库浏览"
      description="浏览本地图库的全部图片，按入库顺序倒序排列，最新导入的图片排在最前。"
    />

    <StatusBar :text="statusText" :loading="loading" />
    <SearchResultGrid :items="images" :empty-text="emptyText" @add-to-board="openBoardPicker" />

    <PaginationBar
      v-if="total > 0"
      :page="page"
      :total-pages="totalPages"
      :total-results="total"
      @change="onPageChange"
    />

    <BoardPicker
      v-if="pickerVisible"
      :image-id="pickerImageId"
      :anchor="pickerAnchor"
      @close="pickerVisible = false"
      @added="onAddedToBoard"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue'
import PageHeader from '@/components/layout/PageHeader.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import PaginationBar from '@/components/common/PaginationBar.vue'
import SearchResultGrid from '@/components/search/SearchResultGrid.vue'
import BoardPicker from '@/components/board/BoardPicker.vue'
import { browseImages } from '@/api/images'
import type { ImageItem } from '@/types/image'

defineOptions({ name: 'LibraryView' })

const images = ref<ImageItem[]>([])
const page = ref(1)
const total = ref(0)
const totalPages = ref(0)
const loading = ref(false)
const statusText = ref('图库加载中...')

const emptyText = computed(() =>
  loading.value ? '图库加载中...' : '图库还没有图片，去「导入图片」添加一些吧',
)

const pickerVisible = ref(false)
const pickerImageId = ref<number>(0)
const pickerAnchor = ref<{ left: number; top: number }>({ left: 0, top: 0 })

let loadSeq = 0

async function load() {
  const seq = ++loadSeq
  loading.value = true
  statusText.value = '图库加载中...'
  try {
    const data = await browseImages(page.value)
    if (seq !== loadSeq) return
    images.value = data.items
    page.value = data.page
    total.value = data.total
    totalPages.value = data.totalPages
    statusText.value = data.total > 0
      ? `第 ${data.page} / ${data.totalPages} 页，共 ${data.total} 张图片`
      : '图库暂无图片'
  } catch (e: unknown) {
    if (seq !== loadSeq) return
    const msg = e instanceof Error ? e.message : String(e)
    statusText.value = '加载失败: ' + msg
    images.value = []
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  window.scrollTo({ top: 0, behavior: 'smooth' })
  load()
}

function openBoardPicker(payload: { imageId: number; event: MouseEvent }) {
  const rect = (payload.event.currentTarget as HTMLElement).getBoundingClientRect()
  pickerImageId.value = payload.imageId
  pickerAnchor.value = { left: rect.left, top: rect.bottom + 4 }
  pickerVisible.value = true
}

function onAddedToBoard() {
  statusText.value = '已添加到灵感板'
}

onMounted(load)

let firstActivation = true
onActivated(() => {
  if (firstActivation) {
    firstActivation = false
    return
  }
  // 从导入页返回时图库可能已变化，刷新当前页
  load()
})
</script>

<template>
  <section class="view">
    <PageHeader
      title="网络搜图"
      description="从 Pixabay / Pexels 拉取参考图，勾选后批量保存到本地图库。"
    />

    <div class="surface search-panel">
      <RemoteToolbar @search="onSearch" />
      <div v-if="selection.hasSelection" class="save-bar">
        <span class="save-count">已选 <strong>{{ selection.count }}</strong> 张</span>
        <div class="save-actions">
          <n-button quaternary size="small" @click="selection.clear()">清空</n-button>
          <n-button type="primary" :loading="saving" @click="saveSelected">保存到图库</n-button>
        </div>
      </div>
    </div>

    <StatusBar :text="statusText" :loading="searching || saving" />

    <div v-if="photos.length" class="remote-grid">
      <RemoteCard
        v-for="(photo, i) in photos"
        :key="photo.id"
        :photo="photo"
        :selected="selection.isSelected(photo.id)"
        @toggle="selection.toggle(photo)"
        @preview="openPreview(i)"
      />
    </div>
    <EmptyState
      v-else
      :text="searched ? '无结果，换个关键词试试' : '输入关键词，从 Pixabay / Pexels 搜索参考图'"
    />

    <RemotePagination
      v-if="totalPages > 0"
      :page="page"
      :total-pages="totalPages"
      :total-results="totalResults"
      @change="onPageChange"
    />
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { NButton, useMessage } from 'naive-ui'
import PageHeader from '@/components/layout/PageHeader.vue'
import RemoteToolbar from '@/components/remote/RemoteToolbar.vue'
import RemoteCard from '@/components/remote/RemoteCard.vue'
import RemotePagination from '@/components/remote/RemotePagination.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useRemoteSelectionStore } from '@/stores/remoteSelection'
import { useLightboxStore } from '@/stores/lightbox'
import { searchRemote, saveRemote } from '@/api/remote'
import type { RemotePhoto, RemoteSource } from '@/types/remote'

defineOptions({ name: 'RemoteSearchView' })

const message = useMessage()
const selection = useRemoteSelectionStore()
const lightbox = useLightboxStore()

const photos = ref<RemotePhoto[]>([])
const page = ref(1)
const totalPages = ref(0)
const totalResults = ref(0)
const searched = ref(false)
const lastQuery = ref({ source: 'pixabay' as RemoteSource, query: '', perPage: 20 })
const statusText = ref('输入关键词搜索网络图片')
const saving = ref(false)
const searching = ref(false)

async function onSearch(payload: { source: RemoteSource; query: string; perPage: number }) {
  lastQuery.value = payload
  page.value = 1
  await doSearch()
}

async function onPageChange(p: number) {
  page.value = p
  await doSearch()
}

function openPreview(index: number) {
  lightbox.show(
    photos.value.map((p) => ({
      src: p.url || p.thumbnail,
      title: p.photographer || '网络图片',
      subtitle: p.alt || undefined,
    })),
    index,
  )
}

async function doSearch() {
  const { source, query, perPage } = lastQuery.value
  if (!query.trim()) return
  searching.value = true
  statusText.value = '搜索中...'
  try {
    const data = await searchRemote(source, query, page.value, perPage)
    photos.value = data.photos || []
    totalResults.value = data.total_results || 0
    totalPages.value = Math.ceil(totalResults.value / perPage) || 1
    statusText.value = `找到 ${totalResults.value} 张结果`
    searched.value = true
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    statusText.value = '搜索失败: ' + msg
    photos.value = []
    searched.value = true
  } finally {
    searching.value = false
  }
}

async function saveSelected() {
  if (!selection.hasSelection) return
  const items = Array.from(selection.selected.values()).map((p) => ({
    id: p.id,
    url: p.url,
    photographer: p.photographer,
    source: selection.source,
  }))
  saving.value = true
  statusText.value = `正在保存 ${items.length} 张图片...`
  try {
    const data = await saveRemote({ photos: items })
    message.success(
      `保存完成: 新增 ${data.saved} 张, 跳过 ${data.skipped} 张` +
        (data.errors.length > 0 ? `, 失败 ${data.errors.length} 张` : ''),
    )
    if (data.saved > 0) selection.clear()
    statusText.value = '保存完成'
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    message.error('保存失败: ' + msg)
    statusText.value = '保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.search-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 18px 20px 20px;
}

.save-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.save-count {
  font-size: 13px;
  color: var(--text-2);
}

.save-count strong {
  color: var(--brand);
  font-variant-numeric: tabular-nums;
}

.save-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.remote-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 16px;
}
</style>

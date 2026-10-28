<template>
  <div class="remote-toolbar">
    <div class="row">
      <n-select v-model:value="source" :options="sourceOptions" class="source" />
      <n-input
        v-model:value="query"
        placeholder="搜索网络图片，如：城市夜景、水墨画、星空"
        clearable
        @keydown.enter="search"
      >
        <template #prefix>
          <AppIcon name="search" :size="17" />
        </template>
      </n-input>
      <n-button type="primary" :disabled="!query.trim()" @click="search">搜索</n-button>
    </div>
    <div class="opts">
      <span>每页数量</span>
      <n-input-number v-model:value="perPage" :min="5" :max="80" size="small" class="per-page" />
      <span class="divider" />
      <span>勾选结果后可批量保存到本地图库</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { NSelect, NInput, NButton, NInputNumber } from 'naive-ui'
import AppIcon from '@/components/common/AppIcon.vue'
import type { RemoteSource } from '@/types/remote'

const emit = defineEmits<{
  search: [payload: { source: RemoteSource; query: string; perPage: number }]
}>()

const source = ref<RemoteSource>('pixabay')
const query = ref('')
const perPage = ref(20)

const sourceOptions = [
  { label: 'Pixabay', value: 'pixabay' },
  { label: 'Pexels', value: 'pexels' },
]

function search() {
  const q = query.value.trim()
  if (!q) return
  emit('search', { source: source.value, query: q, perPage: perPage.value })
}
</script>

<style scoped>
.remote-toolbar {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.row :deep(.n-input) {
  flex: 1;
}

.source {
  width: 132px;
  flex: none;
}

.opts {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
  color: var(--text-3);
}

.per-page {
  width: 86px;
}

.divider {
  width: 1px;
  height: 12px;
  background: var(--border-strong);
}

@media (max-width: 620px) {
  .row {
    flex-wrap: wrap;
  }

  .row :deep(.n-input) {
    flex: 1 1 100%;
    order: 3;
  }
}
</style>

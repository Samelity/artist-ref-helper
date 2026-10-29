<template>
  <div class="pagination">
    <n-pagination
      :page="page"
      :page-count="totalPages"
      :page-slot="5"
      @update:page="(p: number) => $emit('change', p)"
    />
    <span class="total">共 {{ totalResults }} 张</span>
  </div>
</template>

<script setup lang="ts">
import { NPagination } from 'naive-ui'

defineProps<{
  page: number
  totalPages: number
  totalResults: number
}>()

defineEmits<{ change: [page: number] }>()
</script>

<style scoped>
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  padding-top: 4px;
}

.total {
  font-size: 12.5px;
  color: var(--text-3);
  font-variant-numeric: tabular-nums;
}
</style>

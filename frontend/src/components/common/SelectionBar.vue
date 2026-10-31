<template>
  <div class="selection-bar">
    <span class="selection-count" aria-live="polite">
      已选 <strong>{{ count }}</strong> 张
    </span>
    <div class="selection-actions">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ count: number }>()
</script>

<style scoped>
.selection-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 14px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-xs);
}

.selection-count {
  font-size: 13px;
  color: var(--text-2);
}

.selection-count strong {
  color: var(--brand);
  font-variant-numeric: tabular-nums;
}

.selection-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>

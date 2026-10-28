<template>
  <div class="empty-state">
    <div class="icon">
      <AppIcon name="image" :size="22" />
    </div>
    <p>{{ text }}</p>
  </div>
</template>

<script setup lang="ts">
import AppIcon from './AppIcon.vue'

defineProps<{ text: string }>()
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 20px;
  text-align: center;
  color: var(--text-3);
  font-size: 13.5px;
  background: var(--bg-subtle);
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-md);
}

.icon {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: var(--text-4);
  background: var(--bg-surface);
  box-shadow: var(--shadow-xs);
}

p {
  max-width: 46ch;
}
</style>

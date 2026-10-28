<template>
  <div class="status-bar" role="status">
    <span class="dot" :class="{ pulse: loading }" />
    <span>{{ text }}</span>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ text: string; loading?: boolean }>(), { loading: false })
</script>

<style scoped>
.status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 20px;
  padding: 0 2px;
  font-size: 13px;
  color: var(--text-3);
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-4);
  flex: none;
}

.dot.pulse {
  background: var(--brand);
  animation: pulse 1.1s var(--ease) infinite;
}

@keyframes pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 var(--brand-ring);
  }
  50% {
    box-shadow: 0 0 0 5px transparent;
  }
}

@media (prefers-reduced-motion: reduce) {
  .dot.pulse {
    animation: none;
  }
}
</style>

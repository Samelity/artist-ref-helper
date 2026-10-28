<template>
  <header class="page-header">
    <div class="heading">
      <h1>{{ title }}</h1>
      <p v-if="description" class="desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    description?: string
  }>(),
  { description: '' },
)
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.heading {
  min-width: 0;
}

h1 {
  font-size: 22px;
  font-weight: 650;
  line-height: 1.3;
  letter-spacing: -0.02em;
}

.desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-3);
  max-width: 66ch;
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}
</style>

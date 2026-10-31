<template>
  <n-button
    size="small"
    :disabled="!mode && disabled"
    :theme-overrides="brandSoftButtonTheme"
    @click="mode ? emit('exit') : emit('enter')"
  >
    <template #icon>
      <AppIcon :name="mode ? 'close' : 'boards'" :size="14" />
    </template>
    {{ mode ? '退出选择' : '批量选择' }}
  </n-button>
</template>

<script setup lang="ts">
import { NButton } from 'naive-ui'
import AppIcon from '@/components/common/AppIcon.vue'
import { brandSoftButtonTheme } from '@/ui/buttonTheme'

defineProps<{
  mode: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  enter: []
  exit: []
}>()
</script>

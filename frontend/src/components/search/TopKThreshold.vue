<template>
  <div class="topk-threshold">
    <label class="param" title="每次检索返回的图片数量">
      <span>返回数量</span>
      <n-input-number
        :value="topK"
        @update:value="onTopK"
        :min="1"
        :max="100"
        size="small"
        class="param__input"
      />
    </label>
    <label class="param" title="低于该相似度的结果会被过滤">
      <span>相似度阈值</span>
      <n-input-number
        :value="threshold"
        @update:value="onThreshold"
        :min="0"
        :max="1"
        :step="0.05"
        size="small"
        class="param__input"
      />
    </label>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { NInputNumber } from 'naive-ui'

const props = withDefaults(
  defineProps<{
    defaultTopK?: number
    defaultThreshold?: number
  }>(),
  {
    defaultTopK: 12,
    defaultThreshold: 0.2,
  },
)

const emit = defineEmits<{
  update: [params: { topK: number; threshold: number }]
}>()

const topK = ref(props.defaultTopK)
const threshold = ref(props.defaultThreshold)

function onTopK(v: number | null) {
  topK.value = v ?? props.defaultTopK
}

function onThreshold(v: number | null) {
  threshold.value = v ?? props.defaultThreshold
}

watch(
  [topK, threshold],
  () => {
    emit('update', { topK: topK.value, threshold: threshold.value })
  },
  { immediate: true },
)
</script>

<style scoped>
.topk-threshold {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 20px;
  align-items: center;
}

.param {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--text-3);
  white-space: nowrap;
}

.param__input {
  width: 92px;
}
</style>

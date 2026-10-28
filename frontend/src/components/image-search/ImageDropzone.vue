<template>
  <div
    class="dropzone"
    :class="{ 'is-drag': isDragging, 'has-file': !!previewUrl }"
    role="button"
    tabindex="0"
    @click="openFile"
    @keydown.enter.prevent="openFile"
    @dragover.prevent="isDragging = true"
    @dragenter.prevent="isDragging = true"
    @dragleave.prevent="isDragging = false"
    @drop.prevent="onDrop"
  >
    <template v-if="!previewUrl">
      <div class="dz-icon">
        <AppIcon name="upload" :size="22" />
      </div>
      <p class="dz-title">点击选择图片，或拖拽到此处</p>
      <p class="dz-hint">支持 JPG / PNG / WebP</p>
    </template>
    <div v-else class="dz-preview">
      <img :src="previewUrl" alt="preview" />
      <div class="dz-file">
        <span class="dz-file-name" :title="hint">{{ hint }}</span>
        <button class="dz-change" type="button" @click.stop="openFile">重新选择</button>
      </div>
    </div>
  </div>
  <input ref="fileInputRef" type="file" accept="image/*" style="display: none" @change="onFileChange" />
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import AppIcon from '@/components/common/AppIcon.vue'

const emit = defineEmits<{
  select: [file: File]
}>()

const fileInputRef = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const previewUrl = ref<string>('')
const isDragging = ref(false)

const hint = computed(() => (selectedFile.value ? selectedFile.value.name : '未选择文件'))

function openFile() {
  fileInputRef.value?.click()
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.[0]) pickFile(input.files[0])
}

function onDrop(e: DragEvent) {
  isDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) pickFile(file)
}

function pickFile(file: File) {
  if (!file.type.startsWith('image/')) return
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  emit('select', file)
}

defineExpose({
  reset() {
    selectedFile.value = null
    previewUrl.value = ''
    if (fileInputRef.value) fileInputRef.value.value = ''
  },
})
</script>

<style scoped>
.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 148px;
  padding: 26px 20px;
  text-align: center;
  background: var(--bg-subtle);
  border: 1.5px dashed var(--border-strong);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--dur) var(--ease), border-color var(--dur) var(--ease);
}

.dropzone:hover,
.dropzone:focus-visible,
.dropzone.is-drag {
  background: var(--brand-soft);
  border-color: var(--brand);
  outline: none;
}

.dropzone.has-file {
  padding: 12px;
  background: var(--bg-surface);
  border-style: solid;
}

.dz-icon {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: var(--brand);
  background: var(--brand-soft);
}

.is-drag .dz-icon {
  background: #fff;
}

.dz-title {
  font-size: 13.5px;
  font-weight: 500;
  color: var(--text-2);
}

.dz-hint {
  font-size: 12px;
  color: var(--text-4);
}

.dz-preview {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  text-align: left;
}

.dz-preview img {
  width: 88px;
  height: 88px;
  object-fit: cover;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  flex: none;
}

.dz-file {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.dz-file-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-1);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dz-change {
  align-self: flex-start;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--brand);
  background: var(--brand-soft);
  border: none;
  border-radius: var(--radius-full);
  cursor: pointer;
}

.dz-change:hover {
  background: #e3e6ff;
}
</style>

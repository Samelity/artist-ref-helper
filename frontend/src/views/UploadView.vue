<template>
  <section class="view">
    <PageHeader
      title="导入图片"
      description="批量上传本地图片到图库，自动进行 CLIP 编码并建立索引。"
    />

    <div class="surface upload-panel">
      <div
        class="dropzone"
        :class="{ 'is-drag': isDragging }"
        role="button"
        tabindex="0"
        @click="openFile"
        @keydown.enter.prevent="openFile"
        @dragover.prevent="isDragging = true"
        @dragenter.prevent="isDragging = true"
        @dragleave.prevent="isDragging = false"
        @drop.prevent="onDrop"
      >
        <div class="dz-icon">
          <AppIcon name="upload" :size="24" />
        </div>
        <p class="dz-title">点击选择图片，或拖拽文件到此处</p>
        <p class="dz-hint">支持 JPG / PNG / WebP，可多选</p>
      </div>
      <input
        ref="fileInputRef"
        type="file"
        accept=".jpg,.jpeg,.png,.webp"
        multiple
        style="display: none"
        @change="onFileChange"
      />

      <div v-if="files.length && !uploading" class="file-bar">
        <span class="file-count">
          已选 <strong>{{ files.length }}</strong> 张图片，
          共 {{ formatSize(totalSize) }}
        </span>
        <div class="file-actions">
          <n-button quaternary size="small" @click="clearFiles">清空</n-button>
          <n-button type="primary" :loading="uploading" :disabled="uploading" @click="doUpload">
            开始上传
          </n-button>
        </div>
      </div>

      <div v-if="uploading" class="progress-section">
        <div class="progress-info">
          <span>{{ uploadProgressText }}</span>
          <span v-if="uploadProgress.currentFile" class="progress-file" :title="uploadProgress.currentFile">{{ uploadProgress.currentFile }}</span>
        </div>
        <n-progress type="line" :percentage="uploadProgressPercentage" :show-indicator="false" :height="6" />
      </div>

      <div v-if="files.length" class="preview-grid">
        <div v-for="(item, i) in files" :key="item.id" class="preview-item">
          <img :src="item.thumb" :alt="item.file.name" />
          <span class="preview-name" :title="item.file.name">{{ item.file.name }}</span>
          <button class="preview-remove" type="button" @click="removeFile(i)">
            <AppIcon name="close" :size="14" />
          </button>
        </div>
      </div>
    </div>

    <StatusBar :text="statusText" :loading="uploading || importing" />

    <div class="surface batch-panel">
      <div class="batch-header">
        <div>
          <h3 class="batch-title">局域网批量导入</h3>
          <p class="batch-desc">将图片通过局域网共享复制到服务器的导入目录（含子文件夹），然后点击下方按钮一键入库。</p>
        </div>
        <n-button type="primary" :loading="importing" :disabled="importing" @click="doImport">
          开始导入
        </n-button>
      </div>
      <div v-if="importing" class="progress-section">
        <div class="progress-info">
          <span>{{ progressText }}</span>
          <span v-if="progress.currentFile" class="progress-file" :title="progress.currentFile">{{ progress.currentFile }}</span>
        </div>
        <n-progress type="line" :percentage="progressPercentage" :show-indicator="false" :height="6" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { NButton, NProgress, useMessage } from 'naive-ui'
import PageHeader from '@/components/layout/PageHeader.vue'
import StatusBar from '@/components/common/StatusBar.vue'
import AppIcon from '@/components/common/AppIcon.vue'
import { uploadImages, getUploadStatus, importFromDirectory, getImportStatus } from '@/api/upload'

defineOptions({ name: 'UploadView' })

interface FileItem {
  id: number
  file: File
  thumb: string
}

const message = useMessage()
const fileInputRef = ref<HTMLInputElement | null>(null)
const files = ref<FileItem[]>([])
const isDragging = ref(false)
const uploading = ref(false)
const importing = ref(false)
const statusText = ref('选择或拖拽图片文件到上方区域')
const progress = ref({ current: 0, total: 0, currentFile: '', imported: 0, skipped: 0 })
const uploadProgress = ref({ current: 0, total: 0, currentFile: '', saved: 0, skipped: 0 })
let pollTimer: ReturnType<typeof setInterval> | null = null
let uploadPollTimer: ReturnType<typeof setInterval> | null = null
let nextId = 0

const progressPercentage = computed(() =>
  progress.value.total > 0 ? Math.round((progress.value.current / progress.value.total) * 100) : 0,
)

const progressText = computed(() => {
  const { current, total, imported, skipped } = progress.value
  if (total === 0) return '准备中...'
  return `${current} / ${total}  (已导入 ${imported}, 跳过 ${skipped})`
})

const uploadProgressPercentage = computed(() =>
  uploadProgress.value.total > 0
    ? Math.round((uploadProgress.value.current / uploadProgress.value.total) * 100)
    : 0,
)

const uploadProgressText = computed(() => {
  const { current, total, saved, skipped } = uploadProgress.value
  if (total === 0) return '准备中...'
  return `${current} / ${total}  (已保存 ${saved}, 跳过 ${skipped})`
})

const totalSize = computed(() => files.value.reduce((sum, f) => sum + f.file.size, 0))

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function openFile() {
  fileInputRef.value?.click()
}

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files) addFiles(input.files)
  input.value = ''
}

function onDrop(e: DragEvent) {
  isDragging.value = false
  const dt = e.dataTransfer
  if (dt?.files) addFiles(dt.files)
}

function addFiles(fileList: FileList) {
  const imageExts = ['.jpg', '.jpeg', '.png', '.webp']
  for (const file of Array.from(fileList)) {
    const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()
    if (!imageExts.includes(ext)) continue
    const thumb = URL.createObjectURL(file)
    files.value.push({ id: nextId++, file, thumb })
  }
  statusText.value = `已选 ${files.value.length} 张图片`
}

function removeFile(index: number) {
  const [item] = files.value.splice(index, 1)
  URL.revokeObjectURL(item.thumb)
  if (!files.value.length) {
    statusText.value = '选择或拖拽图片文件到上方区域'
  }
}

function clearFiles() {
  for (const item of files.value) URL.revokeObjectURL(item.thumb)
  files.value = []
  statusText.value = '选择或拖拽图片文件到上方区域'
}

async function doUpload() {
  if (!files.value.length || uploading.value) return
  uploading.value = true
  uploadProgress.value = { current: 0, total: 0, currentFile: '', saved: 0, skipped: 0 }
  statusText.value = '正在发送文件...'

  try {
    const rawFiles = files.value.map((f) => f.file)
    const result = await uploadImages(rawFiles)
    statusText.value = result.message
    startUploadPolling()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    message.error('上传失败: ' + msg)
    statusText.value = '上传失败'
    uploading.value = false
  }
}

async function doImport() {
  if (importing.value) return
  importing.value = true
  progress.value = { current: 0, total: 0, currentFile: '', imported: 0, skipped: 0 }
  statusText.value = '正在启动导入...'

  try {
    const result = await importFromDirectory()
    statusText.value = result.message
    startPolling()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    message.error('导入失败: ' + msg)
    statusText.value = '导入失败'
    importing.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const status = await getImportStatus()
      progress.value = {
        current: status.current,
        total: status.total,
        currentFile: status.current_file,
        imported: status.imported,
        skipped: status.skipped,
      }
      statusText.value = `正在导入... (${status.current}/${status.total})`

      if (!status.running) {
        stopPolling()
        importing.value = false

        if (status.errors.length > 0) {
          message.warning(
            `导入完成: 新增 ${status.imported} 张, 跳过 ${status.skipped} 张, 失败 ${status.errors.length} 张`,
            { duration: 5000 },
          )
        } else if (status.imported === 0 && status.skipped === 0) {
          message.info('导入目录中没有找到新图片')
        } else {
          message.success(`导入完成: 新增 ${status.imported} 张, 跳过 ${status.skipped} 张`)
        }

        statusText.value = `导入完成: 新增 ${status.imported} 张` +
          (status.skipped ? `, 跳过 ${status.skipped} 张` : '') +
          (status.errors.length ? `, 失败 ${status.errors.length} 张` : '')
      }
    } catch {
      // 网络瞬断不中断轮询，下次重试
    }
  }, 500)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startUploadPolling() {
  stopUploadPolling()
  uploadPollTimer = setInterval(async () => {
    try {
      const status = await getUploadStatus()
      uploadProgress.value = {
        current: status.current,
        total: status.total,
        currentFile: status.current_file,
        saved: status.saved,
        skipped: status.skipped,
      }
      statusText.value = `正在上传... (${status.current}/${status.total})`

      if (!status.running) {
        stopUploadPolling()
        uploading.value = false

        if (status.errors.length > 0) {
          message.warning(
            `上传完成: 成功 ${status.saved} 张, 跳过 ${status.skipped} 张, 失败 ${status.errors.length} 张`,
            { duration: 5000 },
          )
        } else if (status.saved === 0 && status.skipped > 0) {
          message.info(`上传完成: 全部 ${status.skipped} 张均为重复图片，已跳过`)
        } else {
          message.success(`上传完成: 成功保存 ${status.saved} 张, 跳过 ${status.skipped} 张`)
        }

        statusText.value = `上传完成: 成功 ${status.saved} 张` +
          (status.skipped ? `, 跳过 ${status.skipped} 张` : '') +
          (status.errors.length ? `, 失败 ${status.errors.length} 张` : '')
        clearFiles()
      }
    } catch {
      // 网络瞬断不中断轮询，下次重试
    }
  }, 500)
}

function stopUploadPolling() {
  if (uploadPollTimer) {
    clearInterval(uploadPollTimer)
    uploadPollTimer = null
  }
}

onUnmounted(() => {
  stopPolling()
  stopUploadPolling()
})
</script>

<style scoped>
.upload-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px 20px 20px;
}

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

.dz-icon {
  width: 46px;
  height: 46px;
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

.file-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.file-count {
  font-size: 13px;
  color: var(--text-2);
}

.file-count strong {
  color: var(--brand);
  font-variant-numeric: tabular-nums;
}

.file-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 12px;
}

.preview-item {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.preview-item img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--bg-subtle);
}

.preview-name {
  font-size: 11.5px;
  color: var(--text-3);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.preview-remove {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  opacity: 0;
  transition: opacity var(--dur) var(--ease);
}

.preview-item:hover .preview-remove {
  opacity: 1;
}

.preview-remove:hover {
  background: rgba(0, 0, 0, 0.75);
}

.batch-panel {
  padding: 18px 20px 20px;
}

.batch-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.batch-title {
  font-size: 14.5px;
  font-weight: 600;
  color: var(--text-1);
  margin: 0 0 6px;
}

.batch-desc {
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--text-3);
  margin: 0;
  max-width: 52ch;
}

.progress-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed var(--border);
}

.progress-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  color: var(--text-2);
  font-variant-numeric: tabular-nums;
}

.progress-file {
  font-size: 12px;
  color: var(--text-4);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 240px;
  flex: none;
}

@media (max-width: 620px) {
  .batch-header {
    flex-direction: column;
  }

  .batch-header :deep(.n-button) {
    align-self: flex-start;
  }
}
</style>

import { ref } from 'vue'
import { useMessage } from 'naive-ui'
import { uploadImages, waitForUploadResult } from '@/api/upload'
import { apiErrorMessage } from '@/api/http'
import { useBoardsStore } from '@/stores/boards'
import { clipTimestamp } from '@/utils/clipboard'
import type { BatchAddResult } from '@/types/board'

function createBatchId(): string {
  return `paste-${clipTimestamp()}-${Math.random().toString(36).slice(2, 8)}`
}

export function useBoardPasteUpload() {
  const store = useBoardsStore()
  const message = useMessage()
  const pasting = ref(false)
  const pasteStatus = ref('')

  async function uploadToBoard(
    boardId: number,
    boardName: string,
    files: File[],
    onAdded?: () => void,
  ): Promise<void> {
    if (pasting.value) {
      message.info('正在上传，请等待完成后再粘贴')
      return
    }

    pasting.value = true
    pasteStatus.value = `正在上传粘贴的 ${files.length} 张图片…`

    try {
      const batchId = createBatchId()
      await uploadImages(files, batchId)

      const status = await waitForUploadResult(batchId, {
        onProgress: (s) => {
          const total = s.total || files.length
          pasteStatus.value = `正在上传粘贴的 ${total} 张图片… (${s.current}/${total})`
        },
      })

      const results = status.results ?? []
      const ids = results
        .map((item) => item.id)
        .filter((id): id is number => id != null)
      const failed = results.filter((item) => item.outcome === 'error').length

      if (!ids.length) {
        const reason = status.errors?.[0]
        if (reason) {
          message.error(`粘贴上传失败: ${reason}`)
        } else {
          message.warning('没有可加入灵感板的图片')
        }
        return
      }

      pasteStatus.value = '正在加入灵感板…'
      let result: BatchAddResult
      try {
        result = await store.addImages(boardId, ids)
      } catch (e) {
        message.error('图片已上传到图库，但加入灵感板失败: ' + apiErrorMessage(e))
        return
      }

      onAdded?.()

      if (result.added > 0) {
        const parts = [`已加入 ${result.added} 张到「${boardName}」`]
        if (result.skipped > 0) parts.push(`${result.skipped} 张已在板中`)
        if (failed > 0) parts.push(`${failed} 张上传失败`)
        const text = parts.join('，')
        if (failed > 0) message.warning(text)
        else message.success(text)
      } else {
        const parts = [`所选图片均已在「${boardName}」中`]
        if (failed > 0) parts.push(`${failed} 张上传失败`)
        message.info(parts.join('，'))
      }
    } catch (e) {
      message.error('粘贴上传失败: ' + apiErrorMessage(e))
    } finally {
      pasting.value = false
      pasteStatus.value = ''
    }
  }

  return { pasting, pasteStatus, uploadToBoard }
}

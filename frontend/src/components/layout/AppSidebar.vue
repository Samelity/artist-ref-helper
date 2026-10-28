<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-mark">
        <AppIcon name="sparkles" :size="19" :stroke-width="1.9" />
      </div>
      <div class="brand-text">
        <strong>CLIP 图库</strong>
        <span>本地参考图检索</span>
      </div>
    </div>

    <nav class="nav">
      <p class="nav-label">检索</p>
      <router-link
        v-for="tab in searchTabs"
        :key="tab.to"
        :to="tab.to"
        class="nav-item"
        :class="{ active: route.name === tab.name }"
      >
        <AppIcon :name="tab.icon" :size="18" />
        <span>{{ tab.label }}</span>
      </router-link>

      <p class="nav-label">收藏</p>
      <router-link
        to="/boards"
        class="nav-item"
        :class="{ active: route.name === 'boards' || route.name === 'board-detail' }"
      >
        <AppIcon name="boards" :size="18" />
        <span>灵感板</span>
      </router-link>
    </nav>

    <div class="sidebar-footer">
      <p class="nav-label">维护</p>
      <button class="maint-btn" type="button" :disabled="rebuilding" @click="handleRebuild">
        <AppIcon name="refresh" :size="15" :class="{ spin: rebuilding }" />
        <span>{{ rebuilding ? '重建中…' : '重建索引' }}</span>
      </button>
      <p class="hint">重新计算全部图片向量，图片较多时耗时较长。</p>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { useDialog, useMessage } from 'naive-ui'
import AppIcon from '@/components/common/AppIcon.vue'
import { rebuildIndex } from '@/api/images'

const route = useRoute()
const dialog = useDialog()
const message = useMessage()
const rebuilding = ref(false)

const searchTabs = [
  { to: '/text', name: 'text', label: '文搜图', icon: 'text' },
  { to: '/image', name: 'image', label: '图搜图', icon: 'image' },
  { to: '/remote', name: 'remote', label: '网络搜图', icon: 'globe' },
]

function handleRebuild() {
  if (rebuilding.value) return
  dialog.warning({
    title: '重建 CLIP 索引',
    content: '将重新计算全部图片的向量并覆盖现有索引。图片较多时耗时较长，期间检索结果可能不完整。确定继续吗？',
    positiveText: '开始重建',
    negativeText: '取消',
    onPositiveClick: async () => {
      rebuilding.value = true
      const tip = message.loading('索引重建中…', { duration: 0 })
      try {
        const data = await rebuildIndex()
        message.success(`索引重建完成，共 ${data.indexedCount} 张`)
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : String(e)
        message.error('索引重建失败：' + msg)
      } finally {
        tip.destroy()
        rebuilding.value = false
      }
    },
  })
}
</script>

<style scoped>
.sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 22px 14px 18px;
  background: var(--bg-surface);
  border-right: 1px solid var(--border);
  overflow-y: auto;
}

/* ---- 品牌 ---- */
.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 8px;
  flex: none;
}

.brand-mark {
  width: 36px;
  height: 36px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(135deg, #6366f1 0%, #4f46e5 55%, #4338ca 100%);
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.32);
  flex: none;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
  min-width: 0;
}

.brand-text strong {
  font-size: 15px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--text-1);
}

.brand-text span {
  font-size: 11.5px;
  color: var(--text-3);
  white-space: nowrap;
}

/* ---- 导航 ---- */
.nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--text-4);
  padding: 0 10px;
  margin: 10px 0 6px;
}

.nav-label:first-child {
  margin-top: 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: var(--radius-sm);
  color: var(--text-2);
  font-size: 13.5px;
  font-weight: 500;
  text-decoration: none;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease);
}

.nav-item:hover {
  background: var(--bg-hover);
  color: var(--text-1);
}

.nav-item.active {
  background: var(--brand-soft);
  color: var(--brand);
  font-weight: 600;
}

/* ---- 维护区 ---- */
.sidebar-footer {
  margin-top: auto;
  padding-top: 14px;
  border-top: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: none;
}

.sidebar-footer .nav-label {
  margin: 0 0 2px;
}

.maint-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 100%;
  padding: 8px 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-2);
  background: transparent;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--dur) var(--ease), color var(--dur) var(--ease),
    border-color var(--dur) var(--ease);
}

.maint-btn:hover:not(:disabled) {
  background: var(--bg-hover);
  border-color: var(--text-4);
  color: var(--text-1);
}

.maint-btn:disabled {
  opacity: 0.6;
  cursor: progress;
}

.hint {
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--text-4);
  padding: 0 2px;
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spin {
    animation: none;
  }
}

/* ---- 窄屏：顶部横栏 ---- */
@media (max-width: 960px) {
  .sidebar {
    position: sticky;
    height: auto;
    flex-direction: row;
    align-items: center;
    gap: 12px;
    padding: 10px 16px;
    border-right: none;
    border-bottom: 1px solid var(--border);
    overflow: visible;
    z-index: 20;
    background: rgba(255, 255, 255, 0.94);
    backdrop-filter: saturate(180%) blur(12px);
  }

  .brand {
    padding: 0;
    gap: 9px;
  }

  .brand-mark {
    width: 32px;
    height: 32px;
    border-radius: 9px;
  }

  .brand-text span {
    display: none;
  }

  .nav {
    flex: 1;
    flex-direction: row;
    gap: 2px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .nav::-webkit-scrollbar {
    display: none;
  }

  .nav-item {
    padding: 7px 10px;
    white-space: nowrap;
  }

  .nav-label,
  .hint {
    display: none;
  }

  .sidebar-footer {
    margin-top: 0;
    padding-top: 0;
    border-top: none;
    flex-direction: row;
  }

  .maint-btn {
    width: auto;
    white-space: nowrap;
    border-color: transparent;
    background: var(--bg-hover);
  }
}

@media (max-width: 620px) {
  .brand-text {
    display: none;
  }

  .nav-item span {
    display: none;
  }

  .nav-item {
    padding: 8px;
  }

  .maint-btn span {
    display: none;
  }

  .maint-btn {
    padding: 8px;
  }
}
</style>

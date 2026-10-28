<template>
  <div class="shell">
    <AppSidebar />
    <main class="main">
      <div class="main-inner">
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </div>
    </main>
    <ImageLightbox />
  </div>
</template>

<script setup lang="ts">
import AppSidebar from './components/layout/AppSidebar.vue'
import ImageLightbox from './components/common/ImageLightbox.vue'
</script>

<style scoped>
.shell {
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  min-height: 100vh;
}

.main {
  min-width: 0;
  padding: 32px 36px 64px;
}

.main-inner {
  max-width: var(--content-max);
  margin: 0 auto;
}

@media (max-width: 960px) {
  .shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .main {
    padding: 22px 18px 48px;
  }
}
</style>

/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, unknown>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_JAVA_BASE: string
  readonly VITE_PYTHON_BASE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

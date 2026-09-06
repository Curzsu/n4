import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, fileURLToPath(new URL('..', import.meta.url)), '')
  const backendPort = process.env.VITE_BACKEND_PORT || env.VITE_BACKEND_PORT || env.SERVER_PORT || process.env.SERVER_PORT || '8088'

  return {
    cacheDir: '.vite-cache',
    plugins: [
      vue(),
      // Element Plus on-demand: template components are auto-imported with
      // their styles; ElMessage/ElMessageBox keep explicit imports in code and
      // load their styles from main.ts. The v-loading directive is registered
      // manually in main.ts (resolvers cover components only).
      AutoImport({
        resolvers: [ElementPlusResolver()],
        dts: 'src/auto-imports.d.ts',
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/components.d.ts',
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: `http://localhost:${backendPort}`,
          changeOrigin: true,
        },
        '/ws': {
          target: `ws://localhost:${backendPort}`,
          ws: true,
          changeOrigin: true,
        },
      },
    },
    css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@use "@/styles/variables" as *;`,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        // Split the big vendors so app-code changes don't re-download them.
        manualChunks(id) {
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
          if (id.includes('echarts') || id.includes('vue-echarts') || id.includes('zrender')) return 'echarts'
          if (id.includes('node_modules/vue/') || id.includes('@vue/')
              || id.includes('vue-router') || id.includes('pinia')) return 'vue-vendor'
        },
      },
    },
  },
}
})

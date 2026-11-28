import { URL, fileURLToPath } from 'node:url';
import vue from '@vitejs/plugin-vue';
import { playwright } from '@vitest/browser-playwright';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  css: { preprocessorOptions: { scss: { additionalData: '@use "@/styles/scss/global.scss" as *;' } } },
  optimizeDeps: {
    entries: ['src/**/*.test.ts'],
    include: ['dompurify', 'marked', 'vue', 'highlight.js/lib/core', 'highlight.js/lib/languages/*']
  },
  test: {
    attachmentsDir: 'node_modules/.cache/vitest-attachments',
    include: ['src/**/*.test.ts'],
    browser: {
      enabled: true,
      headless: true,
      screenshotFailures: false,
      provider: playwright({ launchOptions: { channel: 'chromium' } }),
      instances: [{ browser: 'chromium' }]
    }
  }
});

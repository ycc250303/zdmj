import { playwright } from '@vitest/browser-playwright';
import { defineConfig } from 'vitest/config';

export default defineConfig({
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

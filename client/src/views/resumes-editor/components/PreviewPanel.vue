<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useResumeStore } from '@/store/modules/resume';
import { $t } from '@/locales';
import StandardTemplate from './templates/StandardTemplate.vue';
import ModernTemplate from './templates/ModernTemplate.vue';

const resumeStore = useResumeStore();

const zoomLevel = ref(1);

const storageKeyColor = 'smarthire_resume_color';
const themeColor = ref(localStorage.getItem(storageKeyColor) || '#2563eb');

watch(themeColor, newColor => {
  localStorage.setItem(storageKeyColor, newColor);
});

const storageKeyTemplate = 'smarthire_resume_template';
const activeTemplate = ref(localStorage.getItem(storageKeyTemplate) || 'modern');

watch(activeTemplate, newTemplate => {
  localStorage.setItem(storageKeyTemplate, newTemplate);
});

const templateOptions = computed(() => [
  { label: $t('page.resume.modernTemplate'), value: 'modern' },
  { label: $t('page.resume.standardTemplate'), value: 'standard' }
]);

const currentComponent = computed(() => {
  if (activeTemplate.value === 'standard') return StandardTemplate;
  if (activeTemplate.value === 'modern') return ModernTemplate;
  return ModernTemplate;
});

const predefineColors = ['#2563eb', '#0f172a', '#059669', '#7c3aed', '#dc2626'];

// 声明我们要抓取的 DOM 节点
const printableRef = ref<HTMLElement | null>(null);

// 终极版 iframe 打印方案
const handleExportPdf = () => {
  if (!printableRef.value) {
    window.$message?.error('未找到简历内容！');
    return;
  }

  // 1. 获取带有样式的外层 HTML（使用 outerHTML 保留容器本身的主题色变量）
  const printContent = printableRef.value.outerHTML;

  // 2. 抓取当前整个 Vue 项目打包后的所有 CSS 样式表
  const styles = Array.from(document.querySelectorAll('style, link[rel="stylesheet"]'))
    .map(el => el.outerHTML)
    .join('\n');

  // 3. 动态创建一个“隐形”的 iframe 放到页面里
  const iframe = document.createElement('iframe');
  iframe.style.position = 'absolute';
  iframe.style.width = '0';
  iframe.style.height = '0';
  iframe.style.border = 'none';
  iframe.style.zIndex = '-9999';
  document.body.appendChild(iframe);

  // 4. 把我们的简历和提取的 CSS 写进这个独立的 iframe 空间
  const doc = iframe.contentWindow?.document;
  if (!doc) return;

  doc.open();
  doc.write(`
    <!DOCTYPE html>
    <html>
      <head>
        <title>简历导出 - ${resumeStore.preview?.personalInfo?.name || 'SmartHire'}</title>
        ${styles}
        <style>
          /* 专门针对 iframe 内部的 A4 纸复位样式 */
          body { 
            margin: 0 !important; 
            padding: 0 !important; 
            background: #ffffff !important; 
          }
          /* 让简历容器贴边，并去除在网页上好看但打印出来会黑一圈的阴影 */
          .printable-area {
            position: absolute !important;
            top: 0 !important;
            left: 0 !important;
            margin: 0 !important;
            box-shadow: none !important;
            transform: none !important;
          }
          /* 消除浏览器自带的网址、页码等页眉页脚 */
          @page {
            size: A4 portrait;
            margin: 0mm; 
          }
        </style>
      </head>
      <body>
        ${printContent}
      </body>
    </html>
  `);
  doc.close();

  // 5. 等待 iframe 里面的 CSS 渲染完毕后，唤起打印
  iframe.onload = () => {
    setTimeout(() => {
      iframe.contentWindow?.focus();
      iframe.contentWindow?.print();

      // 用户关闭打印窗口后，销毁这个 iframe 防止内存泄漏
      setTimeout(() => {
        document.body.removeChild(iframe);
      }, 1000);
    }, 500); // 留 500ms 缓冲，确保字体和样式完全挂载
  };
};
</script>

<template>
  <div class="h-full w-full flex flex-col items-center bg-slate-100 dark:bg-dark-100">
    <div
      class="sticky top-0 z-20 w-full flex items-center justify-between border-b border-slate-200 bg-white p-3 px-6 shadow-sm dark:border-gray-700 dark:bg-dark-200"
    >
      <div class="flex items-center gap-6">
        <div class="flex items-center gap-2">
          <span class="text-sm text-slate-600 font-bold dark:text-gray-400">{{ $t('page.resume.template') }}:</span>
          <NSelect v-model:value="activeTemplate" :options="templateOptions" size="small" class="w-56" />
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm text-slate-600 font-bold dark:text-gray-400">{{ $t('page.resume.color') }}:</span>
          <NColorPicker
            v-model:value="themeColor"
            :swatches="predefineColors"
            size="small"
            class="w-24"
            :show-alpha="false"
          />
        </div>
      </div>

      <div class="flex items-center gap-6">
        <div class="w-48 flex items-center gap-3">
          <span class="cursor-pointer text-slate-400 dark:text-gray-500 hover:text-blue-600" @click="zoomLevel -= 0.1">
            🔍-
          </span>
          <NSlider v-model:value="zoomLevel" :min="0.4" :max="1.5" :step="0.05" :tooltip="false" />
          <span class="cursor-pointer text-slate-400 dark:text-gray-500 hover:text-blue-600" @click="zoomLevel += 0.1">
            🔍+
          </span>
          <span class="w-8 text-xs text-slate-500 font-bold dark:text-gray-400">
            {{ Math.round(zoomLevel * 100) }}%
          </span>
        </div>
        <NButton type="primary" size="small" @click="handleExportPdf">
          <template #icon><span>⬇️</span></template>
          {{ $t('page.resume.exportPdf') }}
        </NButton>
      </div>
    </div>

    <div class="custom-scrollbar w-full flex-1 overflow-y-auto py-8">
      <div
        class="preview-container flex transform-gpu justify-center"
        :style="{
          transform: `scale(${zoomLevel})`,
          transformOrigin: 'top center',
          transition: 'transform 0.1s linear'
        }"
      >
        <div
          ref="printableRef"
          class="printable-area bg-white"
          :style="{
            width: '794px',
            minHeight: '1123px',
            boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1)',
            '--theme-color': themeColor
          }"
        >
          <component :is="currentComponent" v-if="resumeStore.preview" :data="resumeStore.preview" />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 8px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: #cbd5e1;
  border-radius: 20px;
}
</style>

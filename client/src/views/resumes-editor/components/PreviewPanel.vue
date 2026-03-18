<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useResumeStore } from '@/store/modules/resumeStore';
import StandardTemplate from './templates/StandardTemplate.vue';
import ModernTemplate from './templates/ModernTemplate.vue'; 
import { $t } from '@/locales';

const route = useRoute();
const resumeStore = useResumeStore();

const resumeId = route.query.id as string;

const zoomLevel = ref(1); 

const storageKeyColor = `smarthire_resume_color_${resumeId}`;
const savedColor = localStorage.getItem(storageKeyColor);
const themeColor = ref(savedColor || '#2563eb');

watch(themeColor, (newColor) => {
  if (resumeId) localStorage.setItem(storageKeyColor, newColor);
});

const storageKeyTemplate = `smarthire_resume_template_${resumeId}`;
const savedTemplate = localStorage.getItem(storageKeyTemplate);
const activeTemplate = ref(savedTemplate || 'modern');

watch(activeTemplate, (newTemplate) => {
  if (resumeId) localStorage.setItem(storageKeyTemplate, newTemplate);
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
</script>

<template>
  <div class="w-full h-full flex flex-col items-center bg-slate-100">
    
    <div class="w-full bg-white border-b border-slate-200 p-3 flex justify-between items-center sticky top-0 z-20 shadow-sm px-6">
      <div class="flex items-center gap-6">
        <div class="flex items-center gap-2">
          <span class="text-sm font-bold text-slate-600">{{ $t('page.resume.template') }}:</span>
          <NSelect v-model:value="activeTemplate" :options="templateOptions" size="small" class="w-56" />
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-bold text-slate-600">{{ $t('page.resume.color') }}:</span>
          <NColorPicker v-model:value="themeColor" :swatches="predefineColors" size="small" class="w-24" :show-alpha="false" />
        </div>
      </div>

      <div class="flex items-center gap-6">
        <div class="flex items-center gap-3 w-48">
          <div class="i-mdi-magnify-minus text-slate-400 cursor-pointer hover:text-blue-600" @click="zoomLevel -= 0.1"></div>
          <NSlider v-model:value="zoomLevel" :min="0.4" :max="1.5" :step="0.05" :tooltip="false" />
          <div class="i-mdi-magnify-plus text-slate-400 cursor-pointer hover:text-blue-600" @click="zoomLevel += 0.1"></div>
          <span class="text-xs font-bold text-slate-500 w-8">{{ Math.round(zoomLevel * 100) }}%</span>
        </div>
        <NButton type="primary" size="small">
          <template #icon><div class="i-mdi-download"></div></template>
          {{ $t('page.resume.exportPdf') }}
        </NButton>
      </div>
    </div>

    <div class="flex-1 w-full overflow-y-auto py-8 custom-scrollbar">
      <div 
        class="preview-container flex justify-center transform-gpu" 
        :style="{ transform: `scale(${zoomLevel})`, transformOrigin: 'top center', transition: 'transform 0.1s linear' }"
      >
        <div 
          class="bg-white printable-area"
          :style="{ 
            width: '794px', 
            minHeight: '1123px', 
            boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1)',
            '--theme-color': themeColor 
          }"
        >
          <component 
            :is="currentComponent" 
            v-if="resumeStore.resumeData"
            :data="resumeStore.resumeData"
            :personal="resumeStore.personalInfo"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar { width: 8px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background-color: #cbd5e1; border-radius: 20px; }
</style>
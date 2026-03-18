<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { useResumeStore } from '@/store/modules/resumeStore'; 
import EditorPanel from './components/EditorPanel.vue';
import PreviewPanel from './components/PreviewPanel.vue';

defineOptions({ name: 'ResumesEditor' });

const route = useRoute();
const resumeStore = useResumeStore();

onMounted(() => {
  const resumeId = Number(route.query.id);
  if (resumeId) {
    resumeStore.initResumeData(resumeId);
  } else {
    window.$message?.error('缺少简历 ID 参数');
  }
});

onUnmounted(() => {
  resumeStore.resumeData = null;
});
</script>
<template>
  <div class="h-full w-full overflow-hidden bg-slate-100">
    
    <n-split direction="horizontal" :max="0.5" :min="0.2" :default-size="0.35">
      
      <template #1>
        <div class="h-full w-full border-r border-slate-200 bg-white">
          <EditorPanel />
        </div>
      </template>
      
      <template #2>
        <div class="h-full w-full relative">
          <div v-if="resumeStore.isLoading" class="absolute inset-0 flex items-center justify-center bg-white/50 backdrop-blur-sm z-50">
            <NSpin size="large" description="正在加载底层经历数据..." />
          </div>
          <PreviewPanel v-else />
        </div>
      </template>

    </n-split>
  </div>
</template>
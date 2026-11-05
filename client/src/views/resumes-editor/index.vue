<script setup lang="ts">
import { onMounted } from 'vue';
import { useResumeStore } from '@/store/modules/resume';
import { $t } from '@/locales';
import ResumeEditPanel from '@/views/resume-import/components/ResumeEditPanel.vue';
import PreviewPanel from './components/PreviewPanel.vue';

defineOptions({ name: 'ResumesEditor' });

const resumeStore = useResumeStore();

onMounted(async () => {
  const loaded = await resumeStore.load();
  if (loaded && !resumeStore.draft) {
    resumeStore.beginEdit();
  }
});

function save() {
  resumeStore.save(true);
}
</script>

<template>
  <div class="h-full w-full overflow-hidden bg-slate-100">
    <NSplit direction="horizontal" :max="0.5" :min="0.2" :default-size="0.35">
      <template #1>
        <div class="h-full w-full flex flex-col border-r border-slate-200 bg-white">
          <div class="sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-slate-50 p-4">
            <h2 class="text-slate-800 font-bold">{{ $t('page.resume.manageContent') }}</h2>
            <NButton
              type="primary"
              size="medium"
              :loading="resumeStore.isSaving"
              :disabled="!resumeStore.draft"
              @click="save"
            >
              {{ $t('page.resume.saveSync') }}
            </NButton>
          </div>
          <div class="flex-1 overflow-y-auto p-4">
            <ResumeEditPanel v-if="resumeStore.draft" v-model:draft="resumeStore.draft" :show-hint="false" />
          </div>
        </div>
      </template>

      <template #2>
        <div class="relative h-full w-full">
          <div
            v-if="resumeStore.isLoading && !resumeStore.preview"
            class="absolute inset-0 z-50 flex items-center justify-center bg-white/50 backdrop-blur-sm"
          >
            <NSpin size="large" />
          </div>
          <PreviewPanel v-else />
        </div>
      </template>
    </NSplit>
  </div>
</template>

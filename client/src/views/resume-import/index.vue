<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { UploadCustomRequestOptions, UploadFileInfo } from 'naive-ui';
import { fetchUploadFile } from '@/service/api/profile';
import { fetchParseResumeImport } from '@/service/api/resume';
import { useResumeStore } from '@/store/modules/resume';
import { $t } from '@/locales';
import ResumeDetailPanel from './components/ResumeDetailPanel.vue';
import ResumeEditPanel from './components/ResumeEditPanel.vue';

defineOptions({ name: 'ResumeImport' });

const resumeStore = useResumeStore();
const importing = ref(false);
const uploadFileList = ref<UploadFileInfo[]>([]);

function enterEdit() {
  if (!resumeStore.resume || importing.value) return;
  resumeStore.beginEdit();
}

async function handlePdfUpload({ file, onFinish, onError }: UploadCustomRequestOptions) {
  const fail = onError ?? (() => undefined);
  const finish = onFinish ?? (() => undefined);
  const fileObj = file.file;
  if (!fileObj) {
    fail();
    return;
  }
  if (!fileObj.name.toLowerCase().endsWith('.pdf')) {
    window.$message?.warning($t('page.resumeImport.pdfOnly'));
    fail();
    return;
  }

  importing.value = true;
  try {
    const { data: uploadData, error: uploadError } = await fetchUploadFile(fileObj, 'resume');
    if (uploadError || !uploadData?.url) {
      window.$message?.error($t('page.resumeImport.uploadFail'));
      fail();
      return;
    }
    const { data: parseData, error: parseError } = await fetchParseResumeImport({ pdfUrl: uploadData.url });
    if (parseError || !parseData) {
      window.$message?.error($t('page.resumeImport.parseFail'));
      fail();
      return;
    }

    const applied = await resumeStore.applyParsed(parseData);
    if (!applied) {
      fail();
      return;
    }
    uploadFileList.value = [];
    finish();
  } catch {
    window.$message?.error($t('page.resumeImport.parseFail'));
    fail();
  } finally {
    importing.value = false;
  }
}

onMounted(() => {
  resumeStore.load();
});
</script>

<template>
  <div class="h-full min-h-[calc(100vh-120px)] flex flex-col bg-slate-50/80">
    <div class="shrink-0 border-b border-slate-200 bg-white px-6 py-4">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 class="text-xl text-slate-800 font-bold">{{ $t('page.resumeImport.title') }}</h1>
          <p class="mt-1 text-sm text-slate-500">{{ $t('page.resumeImport.subtitle') }}</p>
        </div>
        <div v-if="resumeStore.resume" class="flex shrink-0 items-center gap-2">
          <template v-if="!resumeStore.isEditing">
            <NButton type="primary" :disabled="importing" @click="enterEdit">
              {{ $t('page.resumeImport.edit') }}
            </NButton>
          </template>
          <template v-else>
            <NButton
              type="primary"
              :loading="resumeStore.isSaving"
              :disabled="importing"
              @click="resumeStore.save(false)"
            >
              {{ $t('page.resumeImport.save') }}
            </NButton>
            <NButton :disabled="importing" @click="resumeStore.cancelEdit">
              {{ $t('page.resumeImport.cancelEdit') }}
            </NButton>
          </template>
        </div>
      </div>
    </div>

    <NSpin :show="resumeStore.isLoading" class="min-h-0 flex-1">
      <main class="h-full min-h-[560px] overflow-auto p-6">
        <ResumeEditPanel v-if="resumeStore.isEditing && resumeStore.draft" v-model:draft="resumeStore.draft" />
        <ResumeDetailPanel
          v-else-if="resumeStore.resume"
          v-model:upload-file-list="uploadFileList"
          :resume="resumeStore.resume"
          :parsing="importing"
          @upload="handlePdfUpload"
        />
        <NEmpty v-else-if="!resumeStore.isLoading" class="py-24" :description="$t('page.resumeImport.loadFail')" />
      </main>
    </NSpin>
  </div>
</template>

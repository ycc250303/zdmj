<script setup lang="ts">
import { ref, onMounted } from 'vue';
import type { UploadFileInfo, UploadCustomRequestOptions } from 'naive-ui';
import { $t } from '@/locales';
import { fetchUploadFile } from '@/service/api/profile';
import {
  fetchGetMyResumeContent,
  fetchSaveMyResumeContent,
  fetchParseResumeImport,
  type ResumeApi
} from '@/service/api/resume';
import ResumeDetailPanel from './components/ResumeDetailPanel.vue';
import ResumeEditPanel from './components/ResumeEditPanel.vue';
import {
  buildSaveRequest,
  buildDraftFromImportResult,
  cloneResumeForEdit,
  type ResumeContentDraft
} from './utils/resumeDraft';

defineOptions({ name: 'ResumeImport' });

const loading = ref(false);
const saving = ref(false);
const importing = ref(false);
const isEditing = ref(false);
const resume = ref<ResumeApi.ResumeContentDTO | null>(null);
const draft = ref<ResumeContentDraft | null>(null);
const uploadFileList = ref<UploadFileInfo[]>([]);

async function loadResume() {
  loading.value = true;
  try {
    const { data, error } = await fetchGetMyResumeContent();
    if (error || !data) {
      resume.value = null;
      window.$message?.error($t('page.resumeImport.loadFail'));
      return;
    }
    resume.value = data;
  } finally {
    loading.value = false;
  }
}

function enterEdit() {
  if (!resume.value || importing.value) return;
  draft.value = cloneResumeForEdit(resume.value);
  isEditing.value = true;
}

function cancelEdit() {
  draft.value = null;
  isEditing.value = false;
}

async function handleSave() {
  if (!draft.value) return;

  let payload: ResumeApi.ResumeContentSaveRequest;
  try {
    payload = buildSaveRequest(draft.value);
  } catch (err) {
    const code = err instanceof Error ? err.message : '';
    if (code === 'skillRequired') {
      window.$message?.warning($t('page.resumeImport.skillRequired'));
      return;
    }
    if (code === 'educationIncomplete') {
      window.$message?.warning($t('page.resumeImport.educationIncomplete'));
      return;
    }
    if (code === 'careerIncomplete') {
      window.$message?.warning($t('page.resumeImport.careerIncomplete'));
      return;
    }
    if (code === 'projectIncomplete') {
      window.$message?.warning($t('page.resumeImport.projectIncomplete'));
      return;
    }
    if (code === 'awardIncomplete') {
      window.$message?.warning($t('page.resumeImport.awardIncomplete'));
      return;
    }
    window.$message?.warning($t('page.resumeImport.saveFail'));
    return;
  }

  saving.value = true;
  try {
    const { data, error } = await fetchSaveMyResumeContent(payload);
    if (error || !data) {
      window.$message?.error($t('page.resumeImport.saveFail'));
      return;
    }
    resume.value = data;
    isEditing.value = false;
    draft.value = null;
    window.$message?.success($t('page.resumeImport.saveSuccess'));
  } finally {
    saving.value = false;
  }
}

async function applyImportFromParse(parsed: ResumeApi.ResumeImportParseResult): Promise<boolean> {
  if (!resume.value) return false;

  try {
    const overwriteDraft = buildDraftFromImportResult(resume.value, parsed);
    let payload: ResumeApi.ResumeContentSaveRequest;
    try {
      payload = buildSaveRequest(overwriteDraft);
    } catch (err) {
      const code = err instanceof Error ? err.message : '';
      if (code === 'skillRequired') {
        window.$message?.warning($t('page.resumeImport.skillRequired'));
      } else if (code === 'educationIncomplete') {
        window.$message?.warning($t('page.resumeImport.educationIncomplete'));
      } else if (code === 'careerIncomplete') {
        window.$message?.warning($t('page.resumeImport.careerIncomplete'));
      } else if (code === 'projectIncomplete') {
        window.$message?.warning($t('page.resumeImport.projectIncomplete'));
      } else if (code === 'awardIncomplete') {
        window.$message?.warning($t('page.resumeImport.awardIncomplete'));
      } else {
        window.$message?.warning($t('page.resumeImport.saveFail'));
      }
      return false;
    }

    const { data, error } = await fetchSaveMyResumeContent(payload);
    if (error || !data) {
      window.$message?.error($t('page.resumeImport.applyFail'));
      return false;
    }

    resume.value = data;
    uploadFileList.value = [];
    isEditing.value = false;
    draft.value = null;

    if (parsed.warnings?.length) {
      window.$message?.warning(parsed.warnings[0]);
    }
    window.$message?.success($t('page.resumeImport.applySuccess'));

    return true;
  } catch {
    window.$message?.error($t('page.resumeImport.applyFail'));
    return false;
  }
}

async function handlePdfUpload({ file, onFinish, onError }: UploadCustomRequestOptions) {
  const fileObj = file.file;
  if (!fileObj) {
    onError?.();
    return;
  }
  if (!fileObj.name.toLowerCase().endsWith('.pdf')) {
    window.$message?.warning($t('page.resumeImport.pdfOnly'));
    onError?.();
    return;
  }

  importing.value = true;
  try {
    const { data: uploadData, error: uploadError } = await fetchUploadFile(fileObj, 'resume');
    if (uploadError || !uploadData?.url) {
      window.$message?.error($t('page.resumeImport.uploadFail'));
      onError?.();
      return;
    }
    const { data: parseData, error: parseError } = await fetchParseResumeImport({ pdfUrl: uploadData.url });
    if (parseError || !parseData) {
      window.$message?.error($t('page.resumeImport.parseFail'));
      onError?.();
      return;
    }

    const applied = await applyImportFromParse(parseData);
    if (!applied) {
      onError?.();
      return;
    }
    onFinish?.();
  } catch {
    window.$message?.error($t('page.resumeImport.parseFail'));
    onError?.();
  } finally {
    importing.value = false;
  }
}

onMounted(() => {
  loadResume();
});
</script>

<template>
  <div class="h-full flex flex-col bg-slate-50/80 min-h-[calc(100vh-120px)]">
    <div class="px-6 py-4 border-b border-slate-200 bg-white shrink-0">
      <div class="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 class="text-xl font-bold text-slate-800">{{ $t('page.resumeImport.title') }}</h1>
          <p class="text-sm text-slate-500 mt-1">{{ $t('page.resumeImport.subtitle') }}</p>
        </div>
        <div v-if="resume" class="flex items-center gap-2 shrink-0">
          <template v-if="!isEditing">
            <NButton type="primary" :disabled="importing" @click="enterEdit">
              {{ $t('page.resumeImport.edit') }}
            </NButton>
          </template>
          <template v-else>
            <NButton type="primary" :loading="saving" :disabled="importing" @click="handleSave">
              {{ $t('page.resumeImport.save') }}
            </NButton>
            <NButton :disabled="importing" @click="cancelEdit">{{ $t('page.resumeImport.cancelEdit') }}</NButton>
          </template>
        </div>
      </div>
    </div>

    <NSpin :show="loading" class="flex-1 min-h-0">
      <main class="h-full min-h-[560px] overflow-auto p-6">
        <ResumeEditPanel
          v-if="isEditing && draft"
          v-model:draft="draft"
        />
        <ResumeDetailPanel
          v-else-if="resume"
          v-model:upload-file-list="uploadFileList"
          :resume="resume"
          :parsing="importing"
          @upload="handlePdfUpload"
        />
        <NEmpty
          v-else-if="!loading"
          class="py-24"
          :description="$t('page.resumeImport.loadFail')"
        />
      </main>
    </NSpin>
  </div>
</template>

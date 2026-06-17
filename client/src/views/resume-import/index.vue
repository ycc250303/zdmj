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
    // 不再弹错误 toast：新用户没有简历属于正常状态，由模板降级到导入引导
    if (error || !data) {
      resume.value = null;
      return;
    }
    resume.value = data;
  } finally {
    loading.value = false;
  }
}

function enterEdit() {
  if (!resume.value) return;
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
  <div class="nova-page">
    <header class="nova-page__head">
      <div>
        <span class="nova-eyebrow">// resume</span>
        <h1 class="nova-page__title font-display">{{ $t('page.resumeImport.title') }}</h1>
        <p class="nova-page__sub">{{ $t('page.resumeImport.subtitle') }}</p>
      </div>
      <div v-if="resume" class="flex items-center gap-2 shrink-0">
        <template v-if="!isEditing">
          <NButton type="primary" @click="enterEdit">
            <template #icon>
              <icon-carbon-edit class="text-14px" />
            </template>
            {{ $t('page.resumeImport.edit') }}
          </NButton>
        </template>
        <template v-else>
          <NButton type="primary" :loading="saving" @click="handleSave">
            <template #icon>
              <icon-carbon-save class="text-14px" />
            </template>
            {{ $t('page.resumeImport.save') }}
          </NButton>
          <NButton @click="cancelEdit">{{ $t('page.resumeImport.cancelEdit') }}</NButton>
        </template>
      </div>
    </header>

    <NSpin :show="loading" class="flex-1 min-h-0">
      <main class="nova-page__main">
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
        <div v-else-if="!loading" class="nova-resume-empty">
          <span class="nova-resume-empty__icon">
            <svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
              <path d="M14 2v6h6" />
              <path d="M9 13h6M9 17h6M9 9h2" />
            </svg>
          </span>
          <h2 class="nova-resume-empty__title font-display">还没有简历</h2>
          <p class="nova-resume-empty__desc">
            上传一份 PDF 简历，系统会自动解析教育经历、项目、技能等信息，<br />
            生成结构化简历与你的能力画像。
          </p>
          <NUpload
            :show-file-list="false"
            :file-list="uploadFileList"
            :custom-request="handlePdfUpload"
            accept="application/pdf"
            class="nova-resume-empty__upload"
          >
            <NButton type="primary" size="large" :loading="importing">
              <template #icon>
                <icon-carbon-upload class="text-16px" />
              </template>
              上传 PDF 简历
            </NButton>
          </NUpload>
          <p class="nova-resume-empty__hint font-mono">supported · application/pdf · ≤ 10MB</p>
        </div>
      </main>
    </NSpin>
  </div>
</template>

<style scoped>
.nova-page {
  position: relative;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  padding: 32px 32px 60px;
  color: var(--nova-text);
}

.nova-page__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
  max-width: 1080px;
  width: 100%;
  margin-left: auto;
  margin-right: auto;
}

.nova-page__title {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(120deg, #fff 0%, #c9c4ff 60%, #93f1ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.nova-page__sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--nova-text-faded);
  max-width: 720px;
  line-height: 1.6;
}

.nova-eyebrow {
  display: inline-block;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--nova-violet);
  text-transform: lowercase;
}

.nova-page__main {
  flex: 1;
  max-width: 1080px;
  width: 100%;
  margin: 0 auto;
}

.nova-resume-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 72px 28px;
  border-radius: 22px;
  border: 1px dashed var(--nova-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.03), rgba(255, 255, 255, 0.008));
  gap: 14px;
  margin-top: 8px;
}

.nova-resume-empty__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 76px;
  height: 76px;
  border-radius: 22px;
  background: rgba(124, 92, 255, 0.10);
  border: 1px solid rgba(124, 92, 255, 0.25);
  color: var(--nova-violet);
  margin-bottom: 4px;
}

.nova-resume-empty__title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: #fff;
}

.nova-resume-empty__desc {
  margin: 0;
  font-size: 13.5px;
  color: var(--nova-text-soft);
  line-height: 1.7;
  max-width: 520px;
}

.nova-resume-empty__upload {
  margin-top: 10px;
}

.nova-resume-empty__hint {
  margin-top: 6px;
  font-size: 11px;
  color: var(--nova-text-faded);
  letter-spacing: 0.08em;
  text-transform: lowercase;
}
</style>

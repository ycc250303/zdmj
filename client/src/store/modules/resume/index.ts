import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import { type ResumeApi, fetchGetMyResumeContent, fetchSaveMyResumeContent } from '@/service/api/resume';
import { $t } from '@/locales';
import {
  type ResumeContentDraft,
  buildDraftFromImportResult,
  buildSaveRequest,
  cloneResumeForEdit,
  draftToPreview
} from '@/views/resume-import/utils/resumeDraft';

export const useResumeStore = defineStore('resume', () => {
  const resume = ref<ResumeApi.ResumeContentDTO | null>(null);
  const draft = ref<ResumeContentDraft | null>(null);
  const isLoading = ref(false);
  const isSaving = ref(false);

  const isEditing = computed(() => draft.value !== null);
  const preview = computed(() => (draft.value ? draftToPreview(draft.value) : resume.value));

  async function load() {
    isLoading.value = true;
    try {
      const { data, error } = await fetchGetMyResumeContent();
      if (error || !data) {
        window.$message?.error($t('page.resumeImport.loadFail'));
        return false;
      }
      resume.value = data;
      return true;
    } finally {
      isLoading.value = false;
    }
  }

  function beginEdit() {
    if (!resume.value) return;
    draft.value = cloneResumeForEdit(resume.value);
  }

  function cancelEdit() {
    draft.value = null;
  }

  function notifyInvalidDraft(err: unknown) {
    const code = err instanceof Error ? err.message : '';
    const messages: Record<string, string> = {
      skillRequired: $t('page.resumeImport.skillRequired'),
      educationIncomplete: $t('page.resumeImport.educationIncomplete'),
      careerIncomplete: $t('page.resumeImport.careerIncomplete'),
      projectIncomplete: $t('page.resumeImport.projectIncomplete'),
      awardIncomplete: $t('page.resumeImport.awardIncomplete')
    };
    window.$message?.warning(messages[code] ?? $t('page.resumeImport.saveFail'));
  }

  async function save(keepEditing = false) {
    if (!draft.value) return false;

    let payload: ResumeApi.ResumeContentSaveRequest;
    try {
      payload = buildSaveRequest(draft.value);
    } catch (err) {
      notifyInvalidDraft(err);
      return false;
    }

    isSaving.value = true;
    try {
      const { data, error } = await fetchSaveMyResumeContent(payload);
      if (error || !data) {
        window.$message?.error($t('page.resumeImport.saveFail'));
        return false;
      }
      resume.value = data;
      draft.value = keepEditing ? cloneResumeForEdit(data) : null;
      window.$message?.success(keepEditing ? $t('page.resume.saveSuccess') : $t('page.resumeImport.saveSuccess'));
      return true;
    } finally {
      isSaving.value = false;
    }
  }

  async function applyParsed(parsed: ResumeApi.ResumeImportParseResult) {
    if (!resume.value) return false;

    let payload: ResumeApi.ResumeContentSaveRequest;
    try {
      payload = buildSaveRequest(buildDraftFromImportResult(resume.value, parsed));
    } catch (err) {
      notifyInvalidDraft(err);
      return false;
    }

    isSaving.value = true;
    try {
      const { data, error } = await fetchSaveMyResumeContent(payload);
      if (error || !data) {
        window.$message?.error($t('page.resumeImport.applyFail'));
        return false;
      }
      resume.value = data;
      draft.value = null;
      if (parsed.warnings?.length) {
        window.$message?.warning(parsed.warnings[0]);
      }
      window.$message?.success($t('page.resumeImport.applySuccess'));
      return true;
    } finally {
      isSaving.value = false;
    }
  }

  return {
    resume,
    draft,
    preview,
    isLoading,
    isSaving,
    isEditing,
    load,
    beginEdit,
    cancelEdit,
    save,
    applyParsed
  };
});

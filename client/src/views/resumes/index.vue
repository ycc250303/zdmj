<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';

import { $t } from '@/locales';
import {
  fetchGetResumeList,
  fetchDeleteResume,
  fetchAddResume,
  fetchGetSkillList,
  fetchAddSkill
} from '@/service/api/resume';

defineOptions({ name: 'resumes' });

const router = useRouter();

const resumeList = ref<any[]>([]);
const loading = ref(false);

const hasResume = computed(() => resumeList.value.length > 0);
const myResume = computed(() => resumeList.value[0]);

const showSkillModal = ref(false);
const submitting = ref(false);
const activeTab = ref<'select' | 'create'>('select');
const skillList = ref<any[]>([]);
const selectedSkillId = ref<number | null>(null);

const newSkillForm = reactive({
  name: '',
  content: [{ type: '专业技能', content: ['待补充'] }]
});

async function loadResumeData() {
  loading.value = true;
  try {
    const { data, error } = await fetchGetResumeList();
    if (!error && data) {
      resumeList.value = data || [];
    }
  } catch (err) {
    window.$message?.error($t('page.resume.getFail'));
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadResumeData();
});

async function handleInitCreateResume() {
  loading.value = true;
  const { data, error } = await fetchGetSkillList();
  loading.value = false;

  if (error) {
    return window.$message?.error(`${$t('page.resume.getFail')}: Skills`);
  }

  skillList.value = data || [];

  if (skillList.value.length === 0) {
    activeTab.value = 'create';
  } else {
    activeTab.value = 'select';
    selectedSkillId.value = skillList.value[0].id;
  }

  showSkillModal.value = true;
}

async function handleConfirmCreate() {
  submitting.value = true;
  let targetSkillId = selectedSkillId.value;

  try {
    if (activeTab.value === 'create') {
      if (!newSkillForm.name) {
        window.$message?.warning(`${$t('page.resume.pleaseInput')}${$t('page.resume.formPlaceholderName')}`);
        submitting.value = false;
        return;
      }
      const { data: skillData, error: skillError } = await fetchAddSkill(newSkillForm);
      if (skillError || !skillData?.id) {
        throw new Error(`${$t('page.resume.createFail')}: Skills`);
      }
      targetSkillId = skillData.id;
    }

    if (!targetSkillId) {
      window.$message?.warning($t('page.resume.noSkillAvailable'));
      submitting.value = false;
      return;
    }

    const { data: resumeData, error: resumeError } = await fetchAddResume({
      name: 'SmartHire Standard Resume',
      skillId: targetSkillId
    });

    if (!resumeError && resumeData?.id) {
      showSkillModal.value = false;
      router.push({ name: 'resumes-editor', query: { id: resumeData.id } });
    } else {
      throw new Error(`${$t('page.resume.createFail')}: Resume`);
    }
  } catch (err: any) {
    window.$message?.error(err.message || 'Operation failed');
  } finally {
    submitting.value = false;
  }
}

function handleEdit() {
  if (myResume.value?.id) {
    router.push({ name: 'resumes-editor', query: { id: myResume.value.id } });
  }
}

function handleDelete() {
  if (!myResume.value?.id) return;
  window.$dialog?.warning({
    title: $t('page.resume.dialogDeleteTitle'),
    content: $t('page.resume.dialogDeleteContent'),
    positiveText: $t('page.resume.dialogDeleteConfirm'),
    negativeText: $t('common.cancel'),
    onPositiveClick: async () => {
      loading.value = true;
      const { error } = await fetchDeleteResume(myResume.value.id);
      loading.value = false;
      if (!error) {
        window.$message?.success($t('page.resume.msgDeleteSuccess'));
        loadResumeData();
      } else {
        window.$message?.error($t('page.resume.msgDeleteFail'));
      }
    }
  });
}
</script>

<template>
  <NSpin :show="loading">
    <div class="editorial-resume">
      <div class="grain-bg paper-grain"></div>

      <!-- 章节头 -->
      <header class="section-head">
        <div class="head-meta">
          <span class="head-bar"></span>
          <span class="head-tag">— SECTION 03 / PROFILE —</span>
        </div>
        <h1 class="head-title font-display">{{ $t('page.resume.myResumes') }}</h1>
        <p class="head-byline">— Your story, set in print.</p>
      </header>

      <div class="head-rule"></div>

      <!-- 已有简历：杂志封面式卡片 -->
      <div v-if="hasResume" class="resume-cover">
        <div class="cover-left">
          <div class="cover-stamp font-display">N°<br /><span>{{ String(myResume.id).padStart(3, '0') }}</span></div>
          <div class="cover-decor">
            <div class="dec-circle"></div>
            <div class="dec-line"></div>
          </div>
          <div class="cover-bottom">
            <div class="bottom-tag">— PUBLISHED EDITION —</div>
            <div class="bottom-status">
              <span class="status-dot"></span>
              {{ $t('page.resume.statusReady') }}
            </div>
          </div>
        </div>

        <div class="cover-right">
          <div class="r-eyebrow">— {{ $t('page.resume.tagSmart') }} —</div>
          <h2 class="r-title font-display">{{ myResume.name }}</h2>
          <p class="r-quote font-display">
            "Every résumé is <em>a chapter</em> waiting to be written."
          </p>
          <div class="r-actions">
            <button class="primary-btn" @click="handleEdit">
              {{ $t('page.resume.editResume') }}
              <span class="arrow">→</span>
            </button>
            <button class="ghost-btn-text" @click="handleDelete">
              {{ $t('common.delete') }}
            </button>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else-if="!loading" class="empty-block">
        <div class="empty-no font-display">∅</div>
        <p class="empty-headline font-display">An empty page awaits.</p>
        <p class="empty-sub">{{ $t('page.resume.emptyDesc') }}</p>
        <button class="primary-btn lg" @click="handleInitCreateResume">
          <span class="plus">+</span>
          {{ $t('page.resume.createBtn') }}
        </button>
      </div>

      <!-- 弹窗 -->
      <NModal v-model:show="showSkillModal" preset="card" :title="$t('page.resume.modalTitle')" class="w-[500px]">
        <NTabs v-model:value="activeTab" type="line" animated>
          <NTabPane name="select" :tab="$t('page.resume.tabSelect')" :disabled="skillList.length === 0">
            <div v-if="skillList.length > 0" class="py-4">
              <NRadioGroup v-model:value="selectedSkillId" name="skillRadios">
                <NSpace vertical>
                  <NRadio v-for="skill in skillList" :key="skill.id" :value="skill.id" size="large">
                    {{ skill.name }}
                  </NRadio>
                </NSpace>
              </NRadioGroup>
            </div>
            <div v-else class="text-slate-400 py-4 text-center">{{ $t('page.resume.noSkillAvailable') }}</div>
          </NTabPane>

          <NTabPane name="create" :tab="$t('page.resume.tabCreate')">
            <div class="py-4">
              <NFormItem :label="$t('page.resume.formLabelListName')">
                <NInput v-model:value="newSkillForm.name" :placeholder="$t('page.resume.formPlaceholderName')" />
              </NFormItem>
              <NAlert type="info" class="mt-2 text-xs" :show-icon="false">{{ $t('page.resume.alertSkillTip') }}</NAlert>
            </div>
          </NTabPane>
        </NTabs>

        <template #footer>
          <div class="flex justify-end gap-3">
            <NButton @click="showSkillModal = false">{{ $t('common.cancel') }}</NButton>
            <NButton type="primary" :loading="submitting" @click="handleConfirmCreate">
              {{ $t('page.resume.modalConfirm') }}
            </NButton>
          </div>
        </template>
      </NModal>
    </div>
  </NSpin>
</template>

<style scoped>
.editorial-resume {
  position: relative;
  min-height: 500px;
  height: 100%;
  padding: 40px 56px 56px;
  background: var(--brand-cream);
  color: var(--brand-ink);
}

.grain-bg {
  position: absolute;
  inset: 0;
  opacity: 0.4;
  pointer-events: none;
}

.section-head {
  position: relative;
  z-index: 1;
  margin-bottom: 16px;
}
.head-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.head-bar {
  width: 40px;
  height: 2px;
  background: var(--brand-mocha);
}
.head-tag {
  font-size: 11px;
  letter-spacing: 0.22em;
  color: var(--brand-ink-soft);
}
.head-title {
  font-size: clamp(36px, 4vw, 56px);
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1;
}
.head-byline {
  margin-top: 12px;
  font-family: var(--serif-display);
  font-style: italic;
  color: var(--brand-ink-soft);
  font-size: 15px;
}
.head-rule {
  position: relative;
  height: 1px;
  background: var(--brand-line);
  margin: 28px 0 40px;
}
.head-rule::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 4px;
  height: 1px;
  background: var(--brand-line);
}

/* ============ Cover ============ */
.resume-cover {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 0.85fr 1fr;
  max-width: 1080px;
  background: var(--brand-paper);
  border: 1px solid var(--brand-line);
  border-radius: 4px;
  overflow: hidden;
  box-shadow: var(--brand-shadow-lg);
}

.cover-left {
  position: relative;
  padding: 36px;
  background:
    radial-gradient(circle at 80% 20%, rgba(184, 107, 75, 0.25), transparent 60%),
    linear-gradient(160deg, #efe4d3 0%, #d9bfa0 100%);
  min-height: 360px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}

:global(.dark) .cover-left {
  background:
    radial-gradient(circle at 80% 20%, rgba(184, 107, 75, 0.4), transparent 60%),
    linear-gradient(160deg, #2a2017 0%, #14100c 100%);
}

.cover-stamp {
  position: relative;
  z-index: 2;
  font-size: 22px;
  line-height: 1;
  color: var(--brand-mocha-deep);
  font-style: italic;
}

.cover-stamp span {
  font-size: 64px;
  display: block;
  margin-top: 4px;
  letter-spacing: -0.04em;
}

:global(.dark) .cover-stamp {
  color: #e8b496;
}

.cover-decor {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.dec-circle {
  position: absolute;
  right: -90px;
  top: -90px;
  width: 280px;
  height: 280px;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, var(--brand-mocha), var(--brand-mocha-deep) 70%);
  opacity: 0.7;
}

.dec-line {
  position: absolute;
  left: 36px;
  right: 36px;
  bottom: 100px;
  height: 1px;
  background: rgba(45, 33, 26, 0.3);
}
:global(.dark) .dec-line {
  background: rgba(232, 200, 170, 0.2);
}

.cover-bottom {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bottom-tag {
  font-size: 11px;
  letter-spacing: 0.2em;
  color: var(--brand-ink-soft);
}

.bottom-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--brand-ink);
  font-family: var(--serif-display);
  font-style: italic;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--brand-matcha);
  box-shadow: 0 0 0 4px rgba(63, 138, 106, 0.18);
}

/* Right */
.cover-right {
  padding: 48px 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 18px;
}

.r-eyebrow {
  font-size: 11px;
  letter-spacing: 0.22em;
  color: var(--brand-mocha-deep);
}
:global(.dark) .r-eyebrow {
  color: #e8b496;
}

.r-title {
  font-size: clamp(32px, 3.4vw, 44px);
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.1;
  color: var(--brand-ink);
}

.r-quote {
  font-style: italic;
  font-size: 16px;
  color: var(--brand-ink-soft);
  line-height: 1.7;
  border-left: 2px solid var(--brand-mocha);
  padding-left: 16px;
}

.r-quote em {
  color: var(--brand-mocha-deep);
}

:global(.dark) .r-quote em {
  color: #e8b496;
}

.r-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 8px;
}

/* Buttons */
.primary-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 22px;
  border-radius: 999px;
  background: var(--brand-ink);
  color: var(--brand-paper);
  border: none;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}
.primary-btn:hover {
  background: var(--brand-mocha-deep);
  transform: translateY(-1px);
}
.primary-btn .arrow {
  transition: transform 0.3s ease;
}
.primary-btn:hover .arrow {
  transform: translateX(3px);
}
.primary-btn .plus {
  font-size: 16px;
}
.primary-btn.lg {
  padding: 14px 28px;
  font-size: 15px;
}

.ghost-btn-text {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 13px;
  color: var(--brand-ink-soft);
  font-family: var(--serif-display);
  font-style: italic;
  text-decoration: underline;
  text-decoration-color: rgba(120, 80, 50, 0.3);
  text-underline-offset: 4px;
}
.ghost-btn-text:hover {
  color: #c44536;
  text-decoration-color: #c44536;
}

/* Empty */
.empty-block {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 72px 16px;
}
.empty-no {
  font-size: 88px;
  color: var(--brand-mocha);
  opacity: 0.4;
  line-height: 1;
  margin-bottom: 12px;
}
.empty-headline {
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--brand-ink);
}
.empty-sub {
  font-family: var(--serif-display);
  font-style: italic;
  color: var(--brand-ink-soft);
  margin-bottom: 24px;
}

@media (max-width: 768px) {
  .editorial-resume {
    padding: 24px 20px;
  }
  .resume-cover {
    grid-template-columns: 1fr;
  }
  .cover-left {
    min-height: 240px;
  }
  .cover-right {
    padding: 32px 24px;
  }
}
</style>

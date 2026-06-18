<script setup lang="ts">
import { computed, ref } from 'vue';
import type { UploadCustomRequestOptions, UploadFileInfo } from 'naive-ui';
import { $t } from '@/locales';
import type { ResumeApi } from '@/service/api/resume';

defineOptions({ name: 'ResumeDetailPanel' });

const props = defineProps<{
  resume: ResumeApi.ResumeContentDTO;
  parsing?: boolean;
}>();

const emit = defineEmits<{
  upload: [options: UploadCustomRequestOptions];
}>();

const uploadFileListModel = defineModel<UploadFileInfo[]>('uploadFileList', { required: true });

const pdfInputRef = ref<HTMLInputElement | null>(null);

function triggerPdfPick() {
  if (props.parsing) return;
  pdfInputRef.value?.click();
}

function onPdfFilePicked(event: Event) {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;
  emit('upload', {
    file: {
      id: 'manual-' + Date.now(),
      name: file.name,
      status: 'pending',
      percentage: 0,
      file,
      type: file.type,
      thumbnailUrl: null,
      url: null,
      fullPath: null,
      batchId: null
    } as unknown as UploadFileInfo,
    onFinish: () => {},
    onError: () => {}
  } as UploadCustomRequestOptions);
  if (pdfInputRef.value) pdfInputRef.value.value = '';
}

function formatMonth(dateStr?: string) {
  if (!dateStr) return '';
  return dateStr.length >= 7 ? dateStr.slice(0, 7) : dateStr;
}

function formatRange(start?: string, end?: string) {
  const s = formatMonth(start);
  const e = end ? formatMonth(end) : $t('page.profile.common.present');
  if (!s) return '—';
  return `${s} ~ ${e}`;
}

function degreeLabel(degree: number) {
  switch (degree) {
    case 1:
      return $t('page.profile.education.degrees.phd');
    case 2:
      return $t('page.profile.education.degrees.master');
    case 3:
      return $t('page.profile.education.degrees.bachelor');
    case 4:
      return $t('page.profile.education.degrees.associate');
    case 5:
      return $t('page.profile.education.degrees.highSchool');
    default:
      return $t('page.profile.education.degrees.other');
  }
}

function awardTypeLabel(type: number) {
  switch (type) {
    case 1:
      return $t('page.resume.awardTypeScholarship');
    case 2:
      return $t('page.resume.awardTypeCompetition');
    default:
      return $t('page.resume.awardTypeOther');
  }
}

function formatAwardDate(dateStr?: string) {
  if (!dateStr) return '—';
  return dateStr.length >= 7 ? dateStr.slice(0, 7) : dateStr;
}

const skillGroups = computed(() => props.resume.skill?.content ?? []);
const personalInfo = computed(() => props.resume.personalInfo ?? {});

function displayValue(value?: string) {
  const trimmed = value?.trim();
  return trimmed || '—';
}
</script>

<template>
  <div class="nova-detail">
    <!-- Upload card -->
    <section class="nova-card nova-card--upload">
      <div class="nova-card__head">
        <span class="nova-eyebrow">// import</span>
        <h3 class="nova-card__title">{{ $t('page.resumeImport.uploadSection') }}</h3>
      </div>
      <div class="nova-upload">
        <input
          ref="pdfInputRef"
          type="file"
          accept="application/pdf,.pdf"
          class="hidden"
          @change="onPdfFilePicked"
        />
        <NButton
          type="primary"
          size="medium"
          :loading="parsing"
          class="nova-upload__btn"
          @click="triggerPdfPick"
        >
          <template #icon>
            <icon-carbon-upload class="text-14px" />
          </template>
          {{ parsing ? $t('page.resumeImport.parsing') : $t('page.resumeImport.uploadPdf') }}
        </NButton>
        <p class="nova-upload__hint">{{ $t('page.resumeImport.uploadTip') }}</p>
      </div>
    </section>

    <!-- Basic info -->
    <section class="nova-card">
      <header class="nova-section__head nova-accent--violet">
        <span class="nova-section__bar"></span>
        <h3 class="nova-section__title">{{ $t('page.profile.basicInfo.title') }}</h3>
      </header>
      <div class="nova-grid">
        <div class="nova-field">
          <div class="nova-field__label">{{ $t('page.profile.basicInfo.name') }}</div>
          <div class="nova-field__value">{{ displayValue(personalInfo.name) }}</div>
        </div>
        <div class="nova-field">
          <div class="nova-field__label">{{ $t('page.profile.basicInfo.phone') }}</div>
          <div class="nova-field__value">{{ displayValue(personalInfo.phone) }}</div>
        </div>
        <div class="nova-field">
          <div class="nova-field__label">{{ $t('page.profile.basicInfo.homepageUrl') }}</div>
          <div class="nova-field__value nova-field__value--break">
            <a
              v-if="personalInfo.homepageUrl?.trim()"
              :href="personalInfo.homepageUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="nova-link"
            >
              {{ personalInfo.homepageUrl }}
            </a>
            <span v-else>—</span>
          </div>
        </div>
        <div class="nova-field">
          <div class="nova-field__label">{{ $t('page.profile.basicInfo.preferredWorkCity') }}</div>
          <div class="nova-field__value">{{ displayValue(personalInfo.preferredWorkCity) }}</div>
        </div>
      </div>
    </section>

    <!-- Education -->
    <section class="nova-card">
      <header class="nova-section__head nova-accent--cyan">
        <span class="nova-section__bar"></span>
        <h3 class="nova-section__title">{{ $t('page.resume.education') }}</h3>
      </header>
      <div v-if="!resume.educations?.length" class="nova-empty">{{ $t('page.resumeImport.noSection') }}</div>
      <div v-else class="nova-list">
        <article v-for="edu in resume.educations" :key="edu.id" class="nova-item">
          <div class="nova-item__title">{{ edu.school }}</div>
          <div class="nova-item__meta">{{ edu.major }} · {{ degreeLabel(edu.degree) }}</div>
          <div class="nova-item__date">{{ formatRange(edu.startDate, edu.endDate) }}</div>
          <div v-if="edu.gpa" class="nova-item__sub">GPA: {{ edu.gpa }}</div>
        </article>
      </div>
    </section>

    <!-- Career -->
    <section class="nova-card">
      <header class="nova-section__head nova-accent--mint">
        <span class="nova-section__bar"></span>
        <h3 class="nova-section__title">{{ $t('page.resume.experience') }}</h3>
      </header>
      <div v-if="!resume.careers?.length" class="nova-empty">{{ $t('page.resumeImport.noSection') }}</div>
      <div v-else class="nova-list">
        <article v-for="career in resume.careers" :key="career.id" class="nova-item">
          <div class="nova-item__title">{{ career.company }}</div>
          <div class="nova-item__meta">{{ career.position }}</div>
          <div class="nova-item__date">{{ formatRange(career.startDate, career.endDate) }}</div>
          <div
            v-if="career.details"
            class="nova-item__body"
            v-html="career.details"
          />
        </article>
      </div>
    </section>

    <!-- Projects -->
    <section class="nova-card">
      <header class="nova-section__head nova-accent--violet">
        <span class="nova-section__bar"></span>
        <h3 class="nova-section__title">{{ $t('page.resume.projects') }}</h3>
      </header>
      <div v-if="!resume.projects?.length" class="nova-empty">{{ $t('page.resumeImport.noSection') }}</div>
      <div v-else class="nova-list">
        <article v-for="proj in resume.projects" :key="proj.id" class="nova-item">
          <div class="nova-item__title">{{ proj.name }}</div>
          <div class="nova-item__meta">{{ proj.role }}</div>
          <div class="nova-item__date">{{ formatRange(proj.startDate, proj.endDate) }}</div>
          <p v-if="proj.description" class="nova-item__body">{{ proj.description }}</p>
          <p v-if="proj.contribution" class="nova-item__sub">{{ proj.contribution }}</p>
          <div v-if="proj.techStack?.length" class="nova-tags">
            <NTag v-for="t in proj.techStack" :key="t" size="small" round>{{ t }}</NTag>
          </div>
        </article>
      </div>
    </section>

    <!-- Awards -->
    <section class="nova-card">
      <header class="nova-section__head nova-accent--coral">
        <span class="nova-section__bar"></span>
        <h3 class="nova-section__title">{{ $t('page.resume.awards') }}</h3>
      </header>
      <div v-if="!resume.awards?.length" class="nova-empty">{{ $t('page.resumeImport.noSection') }}</div>
      <div v-else class="nova-list">
        <article v-for="award in resume.awards" :key="award.id" class="nova-item">
          <div class="nova-item__title">{{ award.name }}</div>
          <div class="nova-item__meta">{{ awardTypeLabel(award.awardType) }} · {{ formatAwardDate(award.awardDate) }}</div>
          <p v-if="award.description" class="nova-item__body">{{ award.description }}</p>
        </article>
      </div>
    </section>

    <!-- Skills -->
    <section class="nova-card">
      <header class="nova-section__head nova-accent--amber">
        <span class="nova-section__bar"></span>
        <h3 class="nova-section__title">{{ $t('page.resume.skills') }}</h3>
      </header>
      <div v-if="!skillGroups.length" class="nova-empty">{{ $t('page.resumeImport.noSection') }}</div>
      <div v-else class="nova-list">
        <div v-for="(group, idx) in skillGroups" :key="idx" class="nova-skill-group">
          <div class="nova-skill-group__type">{{ group.type }}</div>
          <div class="nova-tags">
            <NTag v-for="s in group.content" :key="s" size="small" type="info" round>{{ s }}</NTag>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.nova-detail {
  max-width: 880px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.nova-card {
  position: relative;
  border-radius: 20px;
  border: 1px solid var(--nova-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.045) 0%, rgba(255, 255, 255, 0.012) 100%);
  padding: 22px 24px;
  box-shadow: 0 24px 60px -34px rgba(10, 12, 30, 0.65);
}

.nova-card--upload {
  background: linear-gradient(135deg, rgba(124, 92, 255, 0.12) 0%, rgba(34, 211, 238, 0.05) 100%);
  border: 1px solid rgba(124, 92, 255, 0.25);
}

.nova-card__head {
  margin-bottom: 14px;
}

.nova-eyebrow {
  display: inline-block;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--nova-violet);
  text-transform: lowercase;
  margin-bottom: 6px;
}

.nova-card__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--nova-text);
  letter-spacing: -0.005em;
}

.nova-upload {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
}

.nova-upload__btn {
  flex-shrink: 0;
}

.nova-upload__hint {
  margin: 0;
  font-size: 12px;
  color: var(--nova-text-faded);
  letter-spacing: 0.02em;
}

.nova-section__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.nova-section__bar {
  width: 3px;
  height: 16px;
  border-radius: 2px;
  background: var(--accent, var(--nova-violet));
}

.nova-section__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--nova-text);
  letter-spacing: -0.005em;
}

.nova-accent--violet { --accent: var(--nova-violet); }
.nova-accent--cyan { --accent: var(--nova-cyan); }
.nova-accent--mint { --accent: var(--nova-mint); }
.nova-accent--coral { --accent: var(--nova-coral); }
.nova-accent--amber { --accent: #f5c96b; }

.nova-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (max-width: 600px) {
  .nova-grid {
    grid-template-columns: 1fr;
  }
}

.nova-field {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--nova-border);
  border-radius: 12px;
  padding: 12px 14px;
  transition: border-color 0.18s ease, background 0.18s ease;
}

.nova-field:hover {
  border-color: var(--nova-border-strong);
  background: rgba(255, 255, 255, 0.05);
}

.nova-field__label {
  font-size: 11px;
  letter-spacing: 0.06em;
  color: var(--nova-text-faded);
  text-transform: uppercase;
  margin-bottom: 6px;
}

.nova-field__value {
  font-size: 14px;
  color: var(--nova-text);
  font-weight: 500;
  line-height: 1.4;
}

.nova-field__value--break {
  word-break: break-all;
}

.nova-link {
  color: var(--nova-cyan);
  text-decoration: none;
  border-bottom: 1px dashed rgba(34, 211, 238, 0.4);
  transition: color 0.18s ease, border-color 0.18s ease;
}

.nova-link:hover {
  color: #67e8f9;
  border-bottom-color: rgba(103, 232, 249, 0.7);
}

.nova-empty {
  font-size: 13px;
  color: var(--nova-text-faded);
  padding: 18px 0;
  text-align: center;
  font-style: italic;
}

.nova-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.nova-item {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--nova-border);
  border-radius: 14px;
  padding: 14px 16px;
  transition: border-color 0.18s ease, transform 0.18s ease;
}

.nova-item:hover {
  border-color: var(--nova-border-strong);
}

.nova-item__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--nova-text);
  letter-spacing: -0.005em;
}

.nova-item__meta {
  margin-top: 4px;
  font-size: 13px;
  color: var(--nova-text-soft);
}

.nova-item__date {
  margin-top: 4px;
  font-size: 11px;
  color: var(--nova-text-faded);
  font-family: 'JetBrains Mono', monospace;
  letter-spacing: 0.02em;
}

.nova-item__sub {
  margin-top: 6px;
  font-size: 12px;
  color: var(--nova-text-faded);
}

.nova-item__body {
  margin-top: 8px;
  font-size: 13px;
  color: var(--nova-text-soft);
  line-height: 1.65;
  white-space: pre-wrap;
}

.nova-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.nova-skill-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nova-skill-group__type {
  font-size: 13px;
  font-weight: 600;
  color: var(--nova-text);
}

.hidden {
  display: none !important;
}
</style>

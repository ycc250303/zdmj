<script setup lang="ts">
import { computed } from 'vue';
import type { UploadFileInfo, UploadCustomRequestOptions } from 'naive-ui';
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
    case 1: return $t('page.profile.education.degrees.phd');
    case 2: return $t('page.profile.education.degrees.master');
    case 3: return $t('page.profile.education.degrees.bachelor');
    case 4: return $t('page.profile.education.degrees.associate');
    case 5: return $t('page.profile.education.degrees.highSchool');
    default: return $t('page.profile.education.degrees.other');
  }
}

function awardTypeLabel(type: number) {
  switch (type) {
    case 1: return $t('page.resume.awardTypeScholarship');
    case 2: return $t('page.resume.awardTypeCompetition');
    default: return $t('page.resume.awardTypeOther');
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
  <div class="max-w-3xl mx-auto space-y-4">
    <div class="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div class="text-sm font-medium text-slate-700 mb-3">{{ $t('page.resumeImport.uploadSection') }}</div>
      <NUpload
        v-model:file-list="uploadFileListModel"
        accept=".pdf,application/pdf"
        :max="1"
        :disabled="parsing"
        :custom-request="(opts) => emit('upload', opts)"
      >
        <NButton type="primary" :loading="parsing">
          {{ parsing ? $t('page.resumeImport.parsing') : $t('page.resumeImport.uploadPdf') }}
        </NButton>
      </NUpload>
      <p class="text-xs text-slate-400 mt-3 leading-relaxed">{{ $t('page.resumeImport.uploadTip') }}</p>
    </div>

    <NCard class="rounded-2xl shadow-sm">
      <section class="mb-6">
        <h3 class="text-base font-semibold text-slate-800 border-l-4 border-sky-500 pl-3 mb-3">
          {{ $t('page.profile.basicInfo.title') }}
        </h3>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
          <div class="rounded-lg bg-slate-50 p-3 border border-slate-100">
            <div class="text-xs text-slate-400">{{ $t('page.profile.basicInfo.name') }}</div>
            <div class="text-slate-800 mt-1">{{ displayValue(personalInfo.name) }}</div>
          </div>
          <div class="rounded-lg bg-slate-50 p-3 border border-slate-100">
            <div class="text-xs text-slate-400">{{ $t('page.profile.basicInfo.phone') }}</div>
            <div class="text-slate-800 mt-1">{{ displayValue(personalInfo.phone) }}</div>
          </div>
          <div class="rounded-lg bg-slate-50 p-3 border border-slate-100">
            <div class="text-xs text-slate-400">{{ $t('page.profile.basicInfo.homepageUrl') }}</div>
            <div class="text-slate-800 mt-1 break-all">
              <a
                v-if="personalInfo.homepageUrl?.trim()"
                :href="personalInfo.homepageUrl"
                target="_blank"
                rel="noopener noreferrer"
                class="text-primary hover:underline"
              >
                {{ personalInfo.homepageUrl }}
              </a>
              <span v-else>—</span>
            </div>
          </div>
          <div class="rounded-lg bg-slate-50 p-3 border border-slate-100">
            <div class="text-xs text-slate-400">{{ $t('page.profile.basicInfo.preferredWorkCity') }}</div>
            <div class="text-slate-800 mt-1">{{ displayValue(personalInfo.preferredWorkCity) }}</div>
          </div>
        </div>
      </section>

      <section class="mb-6">
        <h3 class="text-base font-semibold text-slate-800 border-l-4 border-primary pl-3 mb-3">
          {{ $t('page.resume.education') }}
        </h3>
        <NEmpty v-if="!resume.educations?.length" size="small" :description="$t('page.resumeImport.noSection')" />
        <div v-else class="space-y-3">
          <div
            v-for="edu in resume.educations"
            :key="edu.id"
            class="rounded-lg bg-slate-50 p-4 border border-slate-100"
          >
            <div class="font-medium text-slate-800">{{ edu.school }}</div>
            <div class="text-sm text-slate-600 mt-1">
              {{ edu.major }} · {{ degreeLabel(edu.degree) }}
            </div>
            <div class="text-xs text-slate-400 mt-1">{{ formatRange(edu.startDate, edu.endDate) }}</div>
            <div v-if="edu.gpa" class="text-xs text-slate-500 mt-1">GPA: {{ edu.gpa }}</div>
          </div>
        </div>
      </section>

      <section class="mb-6">
        <h3 class="text-base font-semibold text-slate-800 border-l-4 border-emerald-500 pl-3 mb-3">
          {{ $t('page.resume.experience') }}
        </h3>
        <NEmpty v-if="!resume.careers?.length" size="small" :description="$t('page.resumeImport.noSection')" />
        <div v-else class="space-y-3">
          <div
            v-for="career in resume.careers"
            :key="career.id"
            class="rounded-lg bg-slate-50 p-4 border border-slate-100"
          >
            <div class="font-medium text-slate-800">{{ career.company }}</div>
            <div class="text-sm text-slate-600">{{ career.position }}</div>
            <div class="text-xs text-slate-400 mt-1">{{ formatRange(career.startDate, career.endDate) }}</div>
            <div
              v-if="career.details"
              class="text-sm text-slate-600 mt-2 whitespace-pre-wrap"
              v-html="career.details"
            />
          </div>
        </div>
      </section>

      <section class="mb-6">
        <h3 class="text-base font-semibold text-slate-800 border-l-4 border-violet-500 pl-3 mb-3">
          {{ $t('page.resume.projects') }}
        </h3>
        <NEmpty v-if="!resume.projects?.length" size="small" :description="$t('page.resumeImport.noSection')" />
        <div v-else class="space-y-3">
          <div
            v-for="proj in resume.projects"
            :key="proj.id"
            class="rounded-lg bg-slate-50 p-4 border border-slate-100"
          >
            <div class="font-medium text-slate-800">{{ proj.name }}</div>
            <div class="text-sm text-slate-600">{{ proj.role }}</div>
            <div class="text-xs text-slate-400 mt-1">{{ formatRange(proj.startDate, proj.endDate) }}</div>
            <p v-if="proj.description" class="text-sm text-slate-600 mt-2">{{ proj.description }}</p>
            <p v-if="proj.contribution" class="text-sm text-slate-500 mt-1">{{ proj.contribution }}</p>
            <div v-if="proj.techStack?.length" class="flex flex-wrap gap-1 mt-2">
              <NTag v-for="t in proj.techStack" :key="t" size="small">{{ t }}</NTag>
            </div>
          </div>
        </div>
      </section>

      <section class="mb-6">
        <h3 class="text-base font-semibold text-slate-800 border-l-4 border-rose-500 pl-3 mb-3">
          {{ $t('page.resume.awards') }}
        </h3>
        <NEmpty v-if="!resume.awards?.length" size="small" :description="$t('page.resumeImport.noSection')" />
        <div v-else class="space-y-3">
          <div
            v-for="award in resume.awards"
            :key="award.id"
            class="rounded-lg bg-slate-50 p-4 border border-slate-100"
          >
            <div class="font-medium text-slate-800">{{ award.name }}</div>
            <div class="text-sm text-slate-600 mt-1">
              {{ awardTypeLabel(award.awardType) }} · {{ formatAwardDate(award.awardDate) }}
            </div>
            <p v-if="award.description" class="text-sm text-slate-600 mt-2 whitespace-pre-wrap">
              {{ award.description }}
            </p>
          </div>
        </div>
      </section>

      <section>
        <h3 class="text-base font-semibold text-slate-800 border-l-4 border-amber-500 pl-3 mb-3">
          {{ $t('page.resume.skills') }}
        </h3>
        <NEmpty v-if="!skillGroups.length" size="small" :description="$t('page.resumeImport.noSection')" />
        <div v-else class="space-y-3">
          <div v-for="(group, idx) in skillGroups" :key="idx">
            <div class="text-sm font-medium text-slate-700">{{ group.type }}</div>
            <div class="flex flex-wrap gap-1 mt-1">
              <NTag v-for="s in group.content" :key="s" size="small" type="info">{{ s }}</NTag>
            </div>
          </div>
        </div>
      </section>
    </NCard>
  </div>
</template>

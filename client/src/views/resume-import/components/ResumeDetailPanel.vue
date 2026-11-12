<script setup lang="ts">
import { computed } from 'vue';
import type { UploadCustomRequestOptions, UploadFileInfo } from 'naive-ui';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';

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

function formatAwardDate(dateStr?: string) {
  if (!dateStr) return '—';
  return dateStr.length >= 7 ? dateStr.slice(0, 7) : dateStr;
}

const skills = computed(() => props.resume.skills ?? []);

const personalInfo = computed(() => props.resume.personalInfo ?? {});

function displayValue(value?: string) {
  const trimmed = value?.trim();
  return trimmed || '—';
}
</script>

<template>
  <div class="mx-auto max-w-3xl space-y-4">
    <div class="border border-slate-200 rounded-2xl bg-white p-4 shadow-sm">
      <div class="mb-3 text-sm text-slate-700 font-medium">{{ $t('page.resumeImport.uploadSection') }}</div>
      <NUpload
        v-model:file-list="uploadFileListModel"
        accept=".pdf,application/pdf"
        :max="1"
        :disabled="parsing"
        :custom-request="opts => emit('upload', opts)"
      >
        <NButton type="primary" :loading="parsing">
          {{ parsing ? $t('page.resumeImport.parsing') : $t('page.resumeImport.uploadPdf') }}
        </NButton>
      </NUpload>
      <p class="mt-3 text-xs text-slate-400 leading-relaxed">{{ $t('page.resumeImport.uploadTip') }}</p>
    </div>

    <NCard class="rounded-2xl shadow-sm">
      <section class="mb-6">
        <h3 class="mb-3 border-l-4 border-sky-500 pl-3 text-base text-slate-800 font-semibold">
          {{ $t('page.profile.basicInfo.title') }}
        </h3>
        <div class="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
          <div class="border border-slate-100 rounded-lg bg-slate-50 p-3">
            <div class="text-xs text-slate-400">{{ $t('page.profile.basicInfo.name') }}</div>
            <div class="mt-1 text-slate-800">{{ displayValue(personalInfo.name) }}</div>
          </div>
          <div class="border border-slate-100 rounded-lg bg-slate-50 p-3">
            <div class="text-xs text-slate-400">{{ $t('page.profile.basicInfo.phone') }}</div>
            <div class="mt-1 text-slate-800">{{ displayValue(personalInfo.phone) }}</div>
          </div>
          <div class="border border-slate-100 rounded-lg bg-slate-50 p-3">
            <div class="text-xs text-slate-400">{{ $t('page.profile.basicInfo.homepageUrl') }}</div>
            <div class="mt-1 break-all text-slate-800">
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
          <div class="border border-slate-100 rounded-lg bg-slate-50 p-3">
            <div class="text-xs text-slate-400">{{ $t('page.profile.basicInfo.preferredWorkCity') }}</div>
            <div class="mt-1 text-slate-800">{{ displayValue(personalInfo.preferredWorkCity) }}</div>
          </div>
        </div>
      </section>

      <section class="mb-6">
        <h3 class="mb-3 border-l-4 border-primary pl-3 text-base text-slate-800 font-semibold">
          {{ $t('page.resume.education') }}
        </h3>
        <NEmpty v-if="!resume.educations?.length" size="small" :description="$t('page.resumeImport.noSection')" />
        <div v-else class="space-y-3">
          <div
            v-for="edu in resume.educations"
            :key="edu.id"
            class="border border-slate-100 rounded-lg bg-slate-50 p-4"
          >
            <div class="text-slate-800 font-medium">{{ edu.school }}</div>
            <div class="mt-1 text-sm text-slate-600">{{ edu.major }} · {{ degreeLabel(edu.degree) }}</div>
            <div class="mt-1 text-xs text-slate-400">{{ formatRange(edu.startDate, edu.endDate) }}</div>
            <div v-if="edu.gpa" class="mt-1 text-xs text-slate-500">GPA: {{ edu.gpa }}</div>
          </div>
        </div>
      </section>

      <section class="mb-6">
        <h3 class="mb-3 border-l-4 border-emerald-500 pl-3 text-base text-slate-800 font-semibold">
          {{ $t('page.resume.experience') }}
        </h3>
        <NEmpty v-if="!resume.careers?.length" size="small" :description="$t('page.resumeImport.noSection')" />
        <div v-else class="space-y-3">
          <div
            v-for="career in resume.careers"
            :key="career.id"
            class="border border-slate-100 rounded-lg bg-slate-50 p-4"
          >
            <div class="text-slate-800 font-medium">{{ career.company }}</div>
            <div class="text-sm text-slate-600">{{ career.position }}</div>
            <div class="mt-1 text-xs text-slate-400">{{ formatRange(career.startDate, career.endDate) }}</div>
            <div
              v-if="career.details"
              class="mt-2 whitespace-pre-wrap text-sm text-slate-600"
              v-html="career.details"
            />
          </div>
        </div>
      </section>

      <section class="mb-6">
        <h3 class="mb-3 border-l-4 border-violet-500 pl-3 text-base text-slate-800 font-semibold">
          {{ $t('page.resume.projects') }}
        </h3>
        <NEmpty v-if="!resume.projects?.length" size="small" :description="$t('page.resumeImport.noSection')" />
        <div v-else class="space-y-3">
          <div
            v-for="proj in resume.projects"
            :key="proj.id"
            class="border border-slate-100 rounded-lg bg-slate-50 p-4"
          >
            <div class="text-slate-800 font-medium">{{ proj.name }}</div>
            <div class="text-sm text-slate-600">{{ proj.role }}</div>
            <div class="mt-1 text-xs text-slate-400">{{ formatRange(proj.startDate, proj.endDate) }}</div>
            <p v-if="proj.description" class="mt-2 text-sm text-slate-600">{{ proj.description }}</p>
            <p v-if="proj.contribution" class="mt-1 text-sm text-slate-500">{{ proj.contribution }}</p>
            <div v-if="proj.techStack?.length" class="mt-2 flex flex-wrap gap-1">
              <NTag v-for="t in proj.techStack" :key="t" size="small">{{ t }}</NTag>
            </div>
            <ul v-if="proj.highlights?.length" class="mt-2 list-disc pl-4 text-sm text-slate-600">
              <li v-for="(item, index) in proj.highlights" :key="index">{{ item }}</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="mb-6">
        <h3 class="mb-3 border-l-4 border-rose-500 pl-3 text-base text-slate-800 font-semibold">
          {{ $t('page.resume.awards') }}
        </h3>
        <NEmpty v-if="!resume.awards?.length" size="small" :description="$t('page.resumeImport.noSection')" />
        <div v-else class="space-y-3">
          <div
            v-for="award in resume.awards"
            :key="award.id"
            class="border border-slate-100 rounded-lg bg-slate-50 p-4"
          >
            <div class="text-slate-800 font-medium">{{ award.name }}</div>
            <div class="mt-1 text-sm text-slate-600">
              {{ formatAwardDate(award.awardDate) }}
            </div>
            <p v-if="award.description" class="mt-2 whitespace-pre-wrap text-sm text-slate-600">
              {{ award.description }}
            </p>
          </div>
        </div>
      </section>

      <section>
        <h3 class="mb-3 border-l-4 border-amber-500 pl-3 text-base text-slate-800 font-semibold">
          {{ $t('page.resume.skills') }}
        </h3>
        <NEmpty v-if="!skills.length" size="small" :description="$t('page.resumeImport.noSection')" />
        <div v-else class="space-y-3">
          <div v-for="skill in skills" :key="skill.id" class="border border-slate-100 rounded-lg bg-slate-50 p-4">
            <div class="text-slate-800 font-medium">{{ skill.name }}</div>
            <p v-if="skill.description" class="mt-2 whitespace-pre-wrap text-sm text-slate-600">
              {{ skill.description }}
            </p>
          </div>
        </div>
      </section>
    </NCard>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import {
  fetchGetJobDetail,
  fetchGetJobCapabilityProfile,
  fetchGenerateJobCapabilityProfile,
  type JobApi
} from '@/service/api/job';

defineOptions({ name: 'job-detail' });

const router = useRouter();
const route = useRoute();

const jobId = computed(() => {
  const id = route.params.id;
  return id ? Number(id) : null;
});

const jobDetail = ref<JobApi.JobListItem | null>(null);
const capabilityProfile = ref<JobApi.JobCapabilityProfile | null>(null);
const loading = ref(false);
const generatingProfile = ref(false);

const salaryTypeOptions = [
  { label: $t('page.jobs.daily') as string, value: 1 },
  { label: $t('page.jobs.monthly') as string, value: 2 },
  { label: $t('page.jobs.yearly') as string, value: 3 }
];

function formatSalary(job: JobApi.JobListItem): string {
  const typeLabel = salaryTypeOptions.find(t => t.value === job.salaryType)?.label || '';
  return `${job.salaryMin}-${job.salaryMax} ${typeLabel}`;
}

async function loadJobDetail() {
  loading.value = true;
  try {
    const { data, error } = await fetchGetJobDetail(jobId.value);
    if (!error && data) {
      jobDetail.value = data;
      // 加载能力画像
      loadCapabilityProfile();
    } else {
      window.$message?.error($t('page.jobs.loadFailed'));
      router.back();
    }
  } catch (err) {
    window.$message?.error($t('page.jobs.loadFailed'));
    router.back();
  } finally {
    loading.value = false;
  }
}

async function loadCapabilityProfile() {
  try {
    const { data, error } = await fetchGetJobCapabilityProfile(jobId.value);
    if (!error && data) {
      capabilityProfile.value = data;
    }
  } catch (err) {
    console.error($t('page.jobs.loadProfileFailed'), err);
  }
}

async function handleGenerateProfile() {
  generatingProfile.value = true;
  window.$message?.info($t('page.jobs.profileGenerating') as string, { duration: 5000 });
  try {
    const { data, error } = await fetchGenerateJobCapabilityProfile(jobId.value);
    if (!error && data) {
      capabilityProfile.value = data;
      window.$message?.success($t('page.jobs.profileGenerated') as string);
    } else {
      window.$message?.error($t('page.jobs.createFailed') as string);
    }
  } catch (err) {
    console.error($t('page.jobs.generateProfileError'), err);
    window.$message?.error($t('page.jobs.createFailed') + $t('page.jobs.retryLater'));
  } finally {
    generatingProfile.value = false;
  }
}

function handleEdit() {
  router.push({ name: 'job-edit', query: { id: jobId.value } });
}

function handleBack() {
  router.back();
}

onMounted(() => {
  if (jobId.value) {
    loadJobDetail();
  } else {
    window.$message?.error($t('page.jobs.formValidation.jobNameRequired'));
    router.push({ name: 'jobs' });
  }
});
</script>

<template>
  <NSpin :show="loading">
    <div class="h-full p-6 bg-slate-50/50 min-h-[500px]">
      <!-- 头部操作栏 -->
      <div class="mb-6 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <NButton quaternary circle @click="handleBack">
            <template #icon><div class="i-mdi-arrow-left"></div></template>
          </NButton>
          <h1 class="text-2xl font-bold text-slate-800">{{ $t('page.jobs.viewDetail') }}</h1>
        </div>
        <div class="flex gap-3">
          <NButton @click="handleGenerateProfile" :loading="generatingProfile">
            <template #icon><div class="i-mdi-brain"></div></template>
            {{ capabilityProfile ? $t('page.jobs.regenerateProfile') : $t('page.jobs.generateProfile') }}
          </NButton>
          <NButton type="primary" @click="handleEdit">
            <template #icon><div class="i-mdi-pencil"></div></template>
            {{ $t('page.jobs.edit') }}
          </NButton>
        </div>
      </div>

      <div v-if="jobDetail" class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- 左侧：岗位信息 -->
        <div class="lg:col-span-2 space-y-6">
          <!-- 基本信息 -->
          <NCard :title="$t('page.jobs.basicInfo')" class="rounded-xl">
            <div class="space-y-4">
              <div class="flex items-center justify-between py-2 border-b border-slate-100">
                <span class="text-slate-600">{{ $t('page.jobs.jobName') }}</span>
                <span class="font-semibold text-slate-800">{{ jobDetail.jobName }}</span>
              </div>
              <div class="flex items-center justify-between py-2 border-b border-slate-100">
                <span class="text-slate-600">{{ $t('page.jobs.companyName') }}</span>
                <span class="font-semibold text-slate-800">{{ jobDetail.companyName }}</span>
              </div>
              <div class="flex items-center justify-between py-2 border-b border-slate-100">
                <span class="text-slate-600">{{ $t('page.jobs.location') }}</span>
                <span class="font-semibold text-slate-800">{{ jobDetail.location }}</span>
              </div>
              <div class="flex items-center justify-between py-2">
                <span class="text-slate-600">{{ $t('page.jobs.salaryInfo') }}</span>
                <span class="font-semibold text-orange-600">{{ formatSalary(jobDetail) }}</span>
              </div>
            </div>
          </NCard>

          <!-- 岗位描述 -->
          <NCard :title="$t('page.jobs.jobDescription')" class="rounded-xl">
            <p class="text-slate-700 leading-relaxed whitespace-pre-wrap">{{ jobDetail.description }}</p>
          </NCard>

          <!-- 岗位关键词 -->
          <NCard v-if="jobDetail.keywords && jobDetail.keywords.length > 0" :title="$t('page.jobs.jobKeywords')" class="rounded-xl">
            <div class="flex flex-wrap gap-2">
              <NTag v-for="keyword in jobDetail.keywords" :key="keyword" type="info" round>
                {{ keyword }}
              </NTag>
            </div>
          </NCard>

          <!-- 岗位职责 -->
          <NCard v-if="jobDetail.jobDuties && jobDetail.jobDuties.length > 0" :title="$t('page.jobs.jobDuties')" class="rounded-xl">
            <ul class="space-y-2">
              <li v-for="(duty, idx) in jobDetail.jobDuties" :key="idx" class="text-slate-700 flex items-start gap-2">
                <span class="text-blue-500 mt-1">•</span>
                <span>{{ duty }}</span>
              </li>
            </ul>
          </NCard>

          <!-- 岗位要求 -->
          <NCard v-if="jobDetail.jobRequirements && jobDetail.jobRequirements.length > 0" :title="$t('page.jobs.jobRequirements')" class="rounded-xl">
            <ul class="space-y-2">
              <li v-for="(req, idx) in jobDetail.jobRequirements" :key="idx" class="text-slate-700 flex items-start gap-2">
                <span class="text-blue-500 mt-1">•</span>
                <span>{{ req }}</span>
              </li>
            </ul>
          </NCard>

          <!-- 公司信息 -->
          <NCard v-if="jobDetail.companyIndustries" :title="$t('page.jobs.companyIndustry') + $t('page.jobs.infoSuffix')" class="rounded-xl">
            <div class="space-y-3">
              <div v-if="jobDetail.companyIndustries.length > 0">
                <span class="text-slate-600">{{ $t('page.jobs.industryLabel') }}</span>
                <NTag v-for="industry in jobDetail.companyIndustries" :key="industry" type="default" class="ml-2">
                  {{ industry }}
                </NTag>
              </div>
            </div>
          </NCard>
        </div>

        <!-- 右侧：能力画像 -->
        <div class="lg:col-span-1">
          <NCard :title="$t('page.jobs.capabilityProfile')" class="rounded-xl h-full">
            <template #header-extra>
              <NButton
                text
                type="primary"
                :loading="generatingProfile"
                @click="handleGenerateProfile"
              >
                <template #icon><div class="i-mdi-refresh"></div></template>
              </NButton>
            </template>

            <div v-if="capabilityProfile" class="space-y-4">
              <!-- 岗位类型 -->
              <div v-if="capabilityProfile.targetRoleType">
                <h4 class="font-semibold text-slate-800 mb-2 flex items-center gap-2">
                  <div class="i-mdi-briefcase text-blue-500"></div>
                  {{ $t('page.jobs.jobName') + $t('page.jobs.jobType') }}
                </h4>
                <p class="text-slate-700">{{ capabilityProfile.targetRoleType }}</p>
              </div>

              <!-- 专业技能 -->
              <div v-if="capabilityProfile.professionalSkills">
                <h4 class="font-semibold text-slate-800 mb-2 flex items-center gap-2">
                  <div class="i-mdi-code-braces text-blue-500"></div>
                  {{ $t('page.jobs.technicalSkills') }}
                </h4>
                <p class="text-slate-700 whitespace-pre-wrap">{{ capabilityProfile.professionalSkills }}</p>
              </div>

              <!-- 证书 -->
              <div v-if="capabilityProfile.certificates">
                <h4 class="font-semibold text-slate-800 mb-2 flex items-center gap-2">
                  <div class="i-mdi-certificate text-purple-500"></div>
                  {{ $t('page.jobs.certificateRequired') }}
                </h4>
                <p class="text-slate-700 whitespace-pre-wrap">{{ capabilityProfile.certificates }}</p>
              </div>

              <!-- 七维能力 -->
              <div class="grid grid-cols-1 gap-3">
                <div v-if="capabilityProfile.innovationAbility" class="bg-blue-50 p-3 rounded-lg">
                  <div class="font-medium text-slate-800 mb-1 flex items-center gap-2">
                    <div class="i-mdi-lightbulb text-blue-500"></div>
                    {{ $t('page.jobs.innovationAbility') }}
                  </div>
                  <p class="text-sm text-slate-700">{{ capabilityProfile.innovationAbility }}</p>
                </div>

                <div v-if="capabilityProfile.learningAbility" class="bg-green-50 p-3 rounded-lg">
                  <div class="font-medium text-slate-800 mb-1 flex items-center gap-2">
                    <div class="i-mdi-school text-green-500"></div>
                    {{ $t('page.jobs.learningAbility') }}
                  </div>
                  <p class="text-sm text-slate-700">{{ capabilityProfile.learningAbility }}</p>
                </div>

                <div v-if="capabilityProfile.pressureResistance" class="bg-orange-50 p-3 rounded-lg">
                  <div class="font-medium text-slate-800 mb-1 flex items-center gap-2">
                    <div class="i-mdi-shield-check text-orange-500"></div>
                    {{ $t('page.jobs.pressureResistance') }}
                  </div>
                  <p class="text-sm text-slate-700">{{ capabilityProfile.pressureResistance }}</p>
                </div>

                <div v-if="capabilityProfile.communicationAbility" class="bg-cyan-50 p-3 rounded-lg">
                  <div class="font-medium text-slate-800 mb-1 flex items-center gap-2">
                    <div class="i-mdi-account-voice text-cyan-500"></div>
                    {{ $t('page.jobs.communicationAbility') }}
                  </div>
                  <p class="text-sm text-slate-700">{{ capabilityProfile.communicationAbility }}</p>
                </div>

                <div v-if="capabilityProfile.practicalAbility" class="bg-indigo-50 p-3 rounded-lg">
                  <div class="font-medium text-slate-800 mb-1 flex items-center gap-2">
                    <div class="i-mdi-hammer text-indigo-500"></div>
                    {{ $t('page.jobs.practicalAbility') }}
                  </div>
                  <p class="text-sm text-slate-700">{{ capabilityProfile.practicalAbility }}</p>
                </div>
              </div>

              <!-- 岗位优势 -->
              <div v-if="capabilityProfile.strengths && capabilityProfile.strengths.length > 0">
                <h4 class="font-semibold text-slate-800 mb-2 flex items-center gap-2">
                  <div class="i-mdi-star text-yellow-500"></div>
                  {{ $t('page.jobs.strengths') }}
                </h4>
                <ul class="space-y-1">
                  <li v-for="(strength, idx) in capabilityProfile.strengths" :key="idx" class="text-sm text-slate-700 flex items-start gap-2">
                    <span class="text-green-500">✓</span>
                    <span>{{ strength }}</span>
                  </li>
                </ul>
              </div>

              <!-- 缺失技能 -->
              <div v-if="capabilityProfile.missingSkills && capabilityProfile.missingSkills.length > 0">
                <h4 class="font-semibold text-slate-800 mb-2 flex items-center gap-2">
                  <div class="i-mdi-alert-circle text-red-500"></div>
                  {{ $t('page.jobs.missingSkills') }}
                </h4>
                <ul class="space-y-1">
                  <li v-for="(skill, idx) in capabilityProfile.missingSkills" :key="idx" class="text-sm text-slate-700 flex items-start gap-2">
                    <span class="text-red-500">✗</span>
                    <span>{{ skill }}</span>
                  </li>
                </ul>
              </div>

              <!-- 证据不足项 -->
              <div v-if="capabilityProfile.weakEvidenceItems && capabilityProfile.weakEvidenceItems.length > 0">
                <h4 class="font-semibold text-slate-800 mb-2 flex items-center gap-2">
                  <div class="i-mdi-help-circle text-orange-500"></div>
                  {{ $t('page.jobs.weakEvidenceItems') }}
                </h4>
                <ul class="space-y-1">
                  <li v-for="(item, idx) in capabilityProfile.weakEvidenceItems" :key="idx" class="text-sm text-slate-700 flex items-start gap-2">
                    <span class="text-orange-500">!</span>
                    <span>{{ item }}</span>
                  </li>
                </ul>
              </div>

              <!-- 总结 -->
              <div v-if="capabilityProfile.summary" class="bg-gradient-to-r from-blue-50 to-indigo-50 p-4 rounded-lg border border-blue-200">
                <h4 class="font-semibold text-slate-800 mb-2 flex items-center gap-2">
                  <div class="i-mdi-text-box-outline text-blue-600"></div>
                  {{ $t('page.jobs.summary') }}
                </h4>
                <p class="text-slate-700 italic">{{ capabilityProfile.summary }}</p>
              </div>
            </div>

            <NEmpty v-else :description="$t('page.jobs.noProfile')" class="py-8">
              <template #extra>
                <NButton type="primary" :loading="generatingProfile" @click="handleGenerateProfile">
                  <template #icon><div class="i-mdi-brain"></div></template>
                  {{ $t('page.jobs.generateProfile') }}
                </NButton>
              </template>
            </NEmpty>
          </NCard>
        </div>
      </div>
    </div>
  </NSpin>
</template>

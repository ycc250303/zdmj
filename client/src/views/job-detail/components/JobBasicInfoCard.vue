<script setup lang="ts">
import type { JobApi } from '@/service/api/job';
import { $t } from '@/locales';
import { NTag } from 'naive-ui';

defineOptions({ name: 'JobBasicInfoCard' });

interface Props {
  jobDetail: JobApi.JobListItem;
}

defineProps<Props>();

function formatSalary(job: JobApi.JobListItem): string {
  const typeMap: Record<number, string> = {
    1: $t('page.jobs.daily'),
    2: $t('page.jobs.monthly'),
    3: $t('page.jobs.yearly')
  };
  const typeLabel = typeMap[job.salaryType as number] || '';
  return `${job.salaryMin}-${job.salaryMax} ${typeLabel}`;
}
</script>

<template>
  <div class="space-y-6">
    <!-- 基本信息 -->
    <div class="airbnb-card p-6">
      <h3 class="text-base font-semibold text-[#222222] dark:text-gray-200 mb-4">{{ $t('page.jobs.basicInfo') }}</h3>
      <div class="space-y-3">
        <div class="flex items-center justify-between py-2 border-b border-[#ebebeb] dark:border-gray-700">
          <span class="text-[#6a6a6a] dark:text-gray-400 text-sm">{{ $t('page.jobs.jobName') }}</span>
          <span class="font-semibold text-[#222222] dark:text-gray-200">{{ jobDetail.jobName }}</span>
        </div>
        <div class="flex items-center justify-between py-2 border-b border-[#ebebeb] dark:border-gray-700">
          <span class="text-[#6a6a6a] dark:text-gray-400 text-sm">{{ $t('page.jobs.companyName') }}</span>
          <span class="font-semibold text-[#222222] dark:text-gray-200">{{ jobDetail.companyName }}</span>
        </div>
        <div class="flex items-center justify-between py-2 border-b border-[#ebebeb] dark:border-gray-700">
          <span class="text-[#6a6a6a] dark:text-gray-400 text-sm">{{ $t('page.jobs.location') }}</span>
          <span class="font-semibold text-[#222222] dark:text-gray-200">{{ jobDetail.location }}</span>
        </div>
        <div class="flex items-center justify-between py-2">
          <span class="text-[#6a6a6a] dark:text-gray-400 text-sm">{{ $t('page.jobs.salaryInfo') }}</span>
          <span class="font-semibold text-[#c4a46c]">{{ formatSalary(jobDetail) }}</span>
        </div>
      </div>
    </div>

    <!-- 岗位描述 -->
    <div class="airbnb-card p-6">
      <h3 class="text-base font-semibold text-[#222222] dark:text-gray-200 mb-4">{{ $t('page.jobs.jobDescription') }}</h3>
      <p class="text-[#3f3f3f] dark:text-gray-300 leading-relaxed whitespace-pre-wrap">{{ jobDetail.description }}</p>
    </div>

    <!-- 岗位关键词 -->
    <div v-if="jobDetail.keywords && jobDetail.keywords.length > 0" class="airbnb-card p-6">
      <h3 class="text-base font-semibold text-[#222222] dark:text-gray-200 mb-3">{{ $t('page.jobs.jobKeywords') }}</h3>
      <div class="flex flex-wrap gap-2">
        <NTag v-for="keyword in jobDetail.keywords" :key="keyword" type="info" round size="small">
          {{ keyword }}
        </NTag>
      </div>
    </div>

    <!-- 岗位职责 -->
    <div v-if="jobDetail.jobDuties && jobDetail.jobDuties.length > 0" class="airbnb-card p-6">
      <h3 class="text-base font-semibold text-[#222222] dark:text-gray-200 mb-3">{{ $t('page.jobs.jobDuties') }}</h3>
      <ul class="space-y-2">
        <li v-for="(duty, idx) in jobDetail.jobDuties" :key="idx" class="text-[#3f3f3f] dark:text-gray-300 flex items-start gap-2">
          <span class="text-[#c4a46c] mt-1 shrink-0">-</span>
          <span>{{ duty }}</span>
        </li>
      </ul>
    </div>

    <!-- 岗位要求 -->
    <div v-if="jobDetail.jobRequirements && jobDetail.jobRequirements.length > 0" class="airbnb-card p-6">
      <h3 class="text-base font-semibold text-[#222222] dark:text-gray-200 mb-3">{{ $t('page.jobs.jobRequirements') }}</h3>
      <ul class="space-y-2">
        <li v-for="(req, idx) in jobDetail.jobRequirements" :key="idx" class="text-[#3f3f3f] dark:text-gray-300 flex items-start gap-2">
          <span class="text-[#c4a46c] mt-1 shrink-0">-</span>
          <span>{{ req }}</span>
        </li>
      </ul>
    </div>

    <!-- 公司信息 -->
    <div v-if="jobDetail.companyIndustries" class="airbnb-card p-6">
      <h3 class="text-base font-semibold text-[#222222] dark:text-gray-200 mb-3">{{ $t('page.jobs.companyIndustry') }}{{ $t('page.jobs.infoSuffix') }}</h3>
      <div>
        <span class="text-[#6a6a6a] dark:text-gray-400 text-sm">{{ $t('page.jobs.industryLabel') }}</span>
        <NTag v-for="industry in jobDetail.companyIndustries" :key="industry" type="default" class="ml-2" size="small">
          {{ industry }}
        </NTag>
      </div>
    </div>
  </div>
</template>

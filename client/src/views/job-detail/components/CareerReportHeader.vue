<script setup lang="ts">
import type { CareerReportApi } from '@/service/api/career-report';
import { $t } from '@/locales';
import { NTag } from 'naive-ui';

defineOptions({ name: 'CareerReportHeader' });

interface Props {
  careerReport: CareerReportApi.CareerReport | null;
  checkResult: CareerReportApi.CareerReportCheck | null;
  generatingReport: boolean;
  checkingReport: boolean;
}

defineProps<Props>();

const emit = defineEmits<{
  generate: [];
  check: [];
}>();

function getStatusLabel(status?: number): string {
  switch (status) {
    case 1: return $t('page.jobs.careerReport.status.draft') as string;
    case 2: return $t('page.jobs.careerReport.status.checked') as string;
    case 3: return $t('page.jobs.careerReport.status.published') as string;
    case 4: return $t('page.jobs.careerReport.status.checkFailed') as string;
    default: return $t('page.jobs.careerReport.status.unknown') as string;
  }
}

function getStatusType(status?: number): 'default' | 'info' | 'success' | 'warning' | 'error' {
  switch (status) {
    case 1: return 'default';
    case 2: return 'success';
    case 3: return 'info';
    case 4: return 'error';
    default: return 'default';
  }
}

function getRiskLabel(level?: string): string {
  switch ((level || '').toLowerCase()) {
    case 'low': return $t('page.jobs.careerReport.riskLow') as string;
    case 'medium': return $t('page.jobs.careerReport.riskMedium') as string;
    case 'high': return $t('page.jobs.careerReport.riskHigh') as string;
    default: return level || '-';
  }
}
</script>

<template>
  <div class="mb-4 flex items-center justify-between flex-wrap gap-3">
    <div class="flex items-center gap-2 flex-wrap">
      <template v-if="careerReport">
        <NTag :type="getStatusType(careerReport.status)" round size="small">
          {{ $t('page.jobs.careerReport.statusLabel') }}：{{ getStatusLabel(careerReport.status) }}
        </NTag>
        <NTag type="info" round size="small">
          {{ $t('page.jobs.careerReport.version') }} v{{ careerReport.version ?? '-' }}
        </NTag>
        <NTag v-if="careerReport.latest" type="success" round size="small">
          {{ $t('page.jobs.careerReport.latest') }}
        </NTag>
        <NTag v-if="careerReport.completenessScore != null" type="warning" round size="small">
          {{ $t('page.jobs.careerReport.completenessScore') }}：{{ careerReport.completenessScore }}/100
        </NTag>
      </template>
    </div>
    <div class="flex gap-2">
      <NButton size="small" type="primary" :loading="generatingReport" @click="emit('generate')">
        {{ careerReport ? $t('page.jobs.careerReport.regenerate') : $t('page.jobs.careerReport.generate') }}
      </NButton>
      <NButton v-if="careerReport" size="small" :loading="checkingReport" @click="emit('check')">
        {{ $t('page.jobs.careerReport.check') }}
      </NButton>
    </div>
  </div>

  <!-- 完整性检查结果 -->
  <div v-if="checkResult" class="airbnb-card p-4 mb-4">
    <div class="space-y-2 text-sm">
      <div class="flex items-center gap-2 flex-wrap">
        <NTag :type="checkResult.passed ? 'success' : 'error'" size="small">
          {{ checkResult.passed ? 'PASS' : 'FAIL' }}
        </NTag>
        <NTag v-if="checkResult.completenessScore != null" type="warning" size="small">
          {{ $t('page.jobs.careerReport.completenessScore') }}：{{ checkResult.completenessScore }}/100
        </NTag>
        <NTag v-if="checkResult.riskLevel" size="small">
          {{ $t('page.jobs.careerReport.riskLevel') }}：{{ getRiskLabel(checkResult.riskLevel) }}
        </NTag>
      </div>
      <div v-if="checkResult.missingSections && checkResult.missingSections.length">
        <span class="font-medium">{{ $t('page.jobs.careerReport.missingSections') }}：</span>
        <NTag v-for="s in checkResult.missingSections" :key="s" size="small" type="warning" class="ml-1">{{ s }}</NTag>
      </div>
      <div v-if="checkResult.nonActionableItems && checkResult.nonActionableItems.length">
        <span class="font-medium">{{ $t('page.jobs.careerReport.nonActionableItems') }}：</span>
        <ul class="list-disc list-inside text-[#3f3f3f] dark:text-gray-300">
          <li v-for="(it, i) in checkResult.nonActionableItems" :key="i">{{ it }}</li>
        </ul>
      </div>
      <div v-if="checkResult.weakEvidenceItems && checkResult.weakEvidenceItems.length">
        <span class="font-medium">{{ $t('page.jobs.careerReport.weakEvidenceItems') }}：</span>
        <ul class="list-disc list-inside text-[#3f3f3f] dark:text-gray-300">
          <li v-for="(it, i) in checkResult.weakEvidenceItems" :key="i">{{ it }}</li>
        </ul>
      </div>
    </div>
  </div>
</template>

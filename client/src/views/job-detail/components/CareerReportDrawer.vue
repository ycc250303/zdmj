<script setup lang="ts">
import type { CareerReportApi } from '@/service/api/career-report';
import { $t } from '@/locales';
import { NDrawer, NDrawerContent, NInput, NButton, NCard, NTag, NEmpty } from 'naive-ui';
import CareerReportHeader from './CareerReportHeader.vue';
import CareerReportEvidenceCard from './CareerReportEvidenceCard.vue';
import CareerReportActionPlan from './CareerReportActionPlan.vue';
import CareerReportCareerPath from './CareerReportCareerPath.vue';
import CareerReportGoals from './CareerReportGoals.vue';
import CareerReportEvaluation from './CareerReportEvaluation.vue';
import CareerReportExploration from './CareerReportExploration.vue';

defineOptions({ name: 'CareerReportDrawer' });

interface Props {
  visible: boolean;
  careerReport: CareerReportApi.CareerReport | null;
  generatingReport: boolean;
  polishingReport: boolean;
  checkingReport: boolean;
  checkResult: CareerReportApi.CareerReportCheck | null;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  'update:visible': [value: boolean];
  generate: [payload: CareerReportApi.CareerReportGenerateReq];
  polish: [instruction: string];
  check: [];
}>();

// 本地表单状态
import { ref } from 'vue';
const localUserPreference = ref('');
const localFocus = ref('');
const localPolishInstruction = ref('');

function handleGenerate() {
  emit('generate', {
    userPreference: localUserPreference.value?.trim() || undefined,
    focus: localFocus.value?.trim() || undefined
  });
}

// --- 结构化数据提取 ---
interface EvidenceData {
  industry: string[];
  technical: string[];
  communication: string[];
}

interface ActionTask {
  task: string;
  cycle: string;
  deliverable: string;
  verification: string;
}

interface ActionPlanData {
  shortTerm: ActionTask[];
  midTerm: ActionTask[];
}

interface CareerPathData {
  entryPoint: string;
  nextStep: string;
  transitionLogic: string;
  alternativePaths: string[];
}

interface GoalsData {
  shortTerm: string;
  midTerm: string;
  longTerm: string;
}

interface EvaluationMetric {
  metric: string;
  target: string;
  deadline: string;
}

interface EvaluationData {
  cycles: string[];
  metrics: EvaluationMetric[];
}

interface ExplorationData {
  roleClarity: string;
  industryInsight: string;
  marketPositioning: string;
}

import { computed } from 'vue';

const evidenceData = computed<EvidenceData | null>(() => {
  const raw = props.careerReport?.reportContent?.evidence;
  if (!raw || typeof raw !== 'object') return null;
  return {
    industry: Array.isArray(raw.industryEvidence) ? raw.industryEvidence : [],
    technical: Array.isArray(raw.technicalEvidence) ? raw.technicalEvidence : [],
    communication: Array.isArray(raw.communicationEvidence) ? raw.communicationEvidence : [],
  };
});

const actionPlanData = computed<ActionPlanData | null>(() => {
  const raw = props.careerReport?.reportContent?.actionPlan;
  if (!raw || typeof raw !== 'object') return null;
  const mapTasks = (arr: any): ActionTask[] => {
    if (!Array.isArray(arr)) return [];
    return arr.map((t: any) => ({
      task: t?.task || '', cycle: t?.cycle || '', deliverable: t?.deliverable || '', verification: t?.verification || '',
    }));
  };
  return { shortTerm: mapTasks(raw.shortTerm), midTerm: mapTasks(raw.midTerm) };
});

const careerPathData = computed<CareerPathData | null>(() => {
  const raw = props.careerReport?.reportContent?.careerPath;
  if (!raw || typeof raw !== 'object') return null;
  return {
    nextStep: (raw.nextStep as string) || '',
    entryPoint: (raw.entryPoint as string) || '',
    transitionLogic: (raw.transitionLogic as string) || '',
    alternativePaths: Array.isArray(raw.alternativePaths) ? raw.alternativePaths : [],
  };
});

const careerGoalsData = computed<GoalsData | null>(() => {
  const raw = props.careerReport?.reportContent?.careerGoals;
  if (!raw || typeof raw !== 'object') return null;
  return {
    shortTerm: (raw.shortTerm as string) || '',
    midTerm: (raw.midTerm as string) || '',
    longTerm: (raw.longTerm as string) || '',
  };
});

const evaluationPlanData = computed<EvaluationData | null>(() => {
  const raw = props.careerReport?.reportContent?.evaluationPlan;
  if (!raw || typeof raw !== 'object') return null;
  const metrics: EvaluationMetric[] = Array.isArray(raw.quantitativeMetrics)
    ? raw.quantitativeMetrics.map((m: any) => ({ metric: m?.metric || '', target: m?.target || '', deadline: m?.deadline || '' }))
    : [];
  const cycles: string[] = Array.isArray(raw.evaluationCycle) ? raw.evaluationCycle : [];
  return { cycles, metrics };
});

const careerExplorationData = computed<ExplorationData | null>(() => {
  const raw = props.careerReport?.reportContent?.careerExploration;
  if (!raw || typeof raw !== 'object') return null;
  return {
    roleClarity: (raw.roleClarity as string) || '',
    industryInsight: (raw.industryInsight as string) || '',
    marketPositioning: (raw.marketPositioning as string) || '',
  };
});

const KNOWN_KEYS = new Set(['evidence', 'actionPlan', 'careerPath', 'careerGoals', 'evaluationPlan', 'careerExploration']);

const unknownSections = computed(() => {
  const content = props.careerReport?.reportContent;
  if (!content || typeof content !== 'object') return [];
  return Object.keys(content).filter(k => !KNOWN_KEYS.has(k)).map(k => ({ key: k, value: (content as any)[k] }));
});

function renderValue(value: any): string {
  if (value == null) return '-';
  if (typeof value === 'string') return value;
  return JSON.stringify(value, null, 2);
}
</script>

<template>
  <NDrawer :show="visible" :width="720" :auto-focus="false" placement="right" @update:show="(v: boolean) => emit('update:visible', v)">
    <NDrawerContent :title="$t('page.jobs.careerReport.drawerTitle')" closable>
      <!-- 状态操作区 -->
      <CareerReportHeader
        :career-report="careerReport"
        :check-result="checkResult"
        :generating-report="generatingReport"
        :checking-report="checkingReport"
        @generate="handleGenerate"
        @check="emit('check')"
      />

      <!-- 空状态 -->
      <NEmpty v-if="!careerReport" :description="$t('page.jobs.careerReport.empty')" class="py-2" />

      <!-- 生成偏好表单 -->
      <NCard size="small" :title="$t('page.jobs.careerReport.userPreferenceLabel') + ' / ' + $t('page.jobs.careerReport.focusLabel')" class="mb-4 rounded-lg">
        <div class="space-y-2">
          <NInput v-model:value="localUserPreference" type="textarea" :placeholder="$t('page.jobs.careerReport.userPreferencePlaceholder') as string" :autosize="{ minRows: 1, maxRows: 3 }" />
          <NInput v-model:value="localFocus" type="textarea" :placeholder="$t('page.jobs.careerReport.focusPlaceholder') as string" :autosize="{ minRows: 1, maxRows: 3 }" />
        </div>
      </NCard>

      <!-- 报告正文 -->
      <template v-if="careerReport">
        <CareerReportEvidenceCard v-if="evidenceData" :data="evidenceData" />
        <CareerReportActionPlan v-if="actionPlanData" :data="actionPlanData" />
        <CareerReportCareerPath v-if="careerPathData" :data="careerPathData" />
        <CareerReportGoals v-if="careerGoalsData" :data="careerGoalsData" />
        <CareerReportEvaluation v-if="evaluationPlanData" :data="evaluationPlanData" />
        <CareerReportExploration v-if="careerExplorationData" :data="careerExplorationData" />

        <!-- 未知章节 -->
        <NCard v-if="unknownSections.length" size="small" :title="$t('page.jobs.careerReport.reportContent')" class="mb-4 rounded-lg">
          <div class="space-y-4">
            <div v-for="sec in unknownSections" :key="sec.key">
              <h4 class="font-semibold text-[#222222] dark:text-gray-200 mb-1">{{ sec.key }}</h4>
              <pre class="whitespace-pre-wrap break-words text-sm text-[#3f3f3f] dark:text-gray-300 bg-[#f7f7f7] dark:bg-gray-700/30 p-3 rounded">{{ renderValue(sec.value) }}</pre>
            </div>
          </div>
        </NCard>
      </template>

      <!-- 知识来源 -->
      <NCard v-if="careerReport?.knowledgeSources?.length" size="small" :title="$t('page.jobs.careerReport.knowledgeSources')" class="mb-4 rounded-lg">
        <ul class="space-y-1 text-sm">
          <li v-for="(src, i) in careerReport.knowledgeSources" :key="i" class="text-[#3f3f3f] dark:text-gray-300">
            <a v-if="src.url" :href="src.url" target="_blank" class="text-[#ff385c] hover:underline">{{ src.title || src.url }}</a>
            <span v-else>{{ src.title || '-' }}</span>
            <span v-if="src.snippet" class="ml-2 text-[#6a6a6a] dark:text-gray-400">— {{ src.snippet }}</span>
          </li>
        </ul>
      </NCard>

      <!-- 润色 -->
      <NCard v-if="careerReport" size="small" :title="$t('page.jobs.careerReport.polish')" class="mb-4 rounded-lg">
        <div class="space-y-2">
          <NInput v-model:value="localPolishInstruction" type="textarea" :placeholder="$t('page.jobs.careerReport.polishInstructionPlaceholder') as string" :autosize="{ minRows: 2, maxRows: 4 }" />
          <div class="flex justify-end">
            <NButton size="small" type="primary" :loading="polishingReport" @click="emit('polish', localPolishInstruction)">
              {{ $t('page.jobs.careerReport.confirmPolish') }}
            </NButton>
          </div>
        </div>
      </NCard>
    </NDrawerContent>
  </NDrawer>
</template>

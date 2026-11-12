<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { type JobApi, fetchGetJobDetail } from '@/service/api/job';
import { type MatchApi, fetchGenerateJobStudentMatch, fetchGetJobStudentMatch } from '@/service/api/match';
import {
  type CareerReportApi,
  fetchCheckCareerReportIntegrity,
  fetchGenerateCareerReport,
  fetchGetLatestCareerReport,
  fetchPolishCareerReport
} from '@/service/api/career-report';
import { parseApiErrorBody } from '@/service/request/api-error';
import { formatJobLocation } from '@/utils/job-display';
import CapabilityScoreCard, { type Dimension } from '@/components/common/CapabilityScoreCard.vue';
import { $t } from '@/locales';

const router = useRouter();
const route = useRoute();

const jobId = computed(() => {
  const id = route.query.id;
  return id ? Number(id) : null;
});

const jobDetail = ref<JobApi.JobListItem | null>(null);
const matchResult = ref<MatchApi.JobStudentMatch | null>(null);
const loading = ref(false);
const generatingMatch = ref(false);

// --- 职业发展报告相关状态 ---
const careerReport = ref<CareerReportApi.CareerReport | null>(null);
const loadingCareerReport = ref(false);
const generatingCareerReport = ref(false);
const polishingCareerReport = ref(false);
const checkingCareerReport = ref(false);
const careerReportDrawerVisible = ref(false);
const careerReportGenerateForm = ref<CareerReportApi.CareerReportGenerateReq>({
  userPreference: '',
  focus: ''
});
const careerReportPolishInstruction = ref('');
const careerReportCheckResult = ref<CareerReportApi.CareerReportCheck | null>(null);

/**
 * 统一从后端错误对象中抽出 code/msg，避免把 LLM 业务错误吞成无意义提示。
 * 兼容两种来源：
 *  1) request 工具返回的 { error } 形态（error 是 AxiosError）
 *  2) try/catch 抛出的 axios 异常
 */
function extractApiError(errLike: any, fallback: string): string {
  const resp = errLike?.response?.data;
  const parsed = parseApiErrorBody(resp);
  const msg: string = parsed.msg || errLike?.message || fallback;
  const code = parsed.code ? `[${parsed.code}] ` : '';
  if (Number(parsed.code) === 11001) {
    return `${code}${msg}（请稍后重试，或确认模型服务可用）`;
  }
  return `${code}${msg}`;
}

function formatSalary(job: JobApi.JobListItem): string {
  const typeMap = {
    1: $t('page.jobs.daily'),
    2: $t('page.jobs.monthly'),
    3: $t('page.jobs.yearly')
  };
  const typeLabel = typeMap[job.salaryType as keyof typeof typeMap] || '';
  return `${job.salaryMin}-${job.salaryMax} ${typeLabel}`;
}

function normalizeJobLink(link?: string | null): string | null {
  const trimmed = link?.trim();
  if (!trimmed) {
    return null;
  }
  return /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
}

async function loadJobDetail() {
  if (!jobId.value) {
    window.$message?.error($t('page.jobs.loadFailed'));
    router.back();
    return;
  }

  loading.value = true;
  try {
    const { data, error } = await fetchGetJobDetail(jobId.value);
    if (!error && data) {
      jobDetail.value = data;
      // 加载人岗匹配结果
      loadMatchResult();
      // 加载职业发展报告（不存在则为 null，不弹错）
      loadCareerReport();
    } else {
      window.$message?.error($t('page.jobs.loadFailed'));
      router.back();
    }
  } catch {
    window.$message?.error($t('page.jobs.loadFailed'));
    router.back();
  } finally {
    loading.value = false;
  }
}

async function loadMatchResult() {
  if (!jobId.value) return;
  try {
    const { data, error } = await fetchGetJobStudentMatch(jobId.value);
    if (!error && data) {
      matchResult.value = data;
    }
  } catch {
    // 匹配结果可能不存在，静默处理
    matchResult.value = null;
  }
}

async function loadCareerReport() {
  if (!jobId.value) return;
  loadingCareerReport.value = true;
  try {
    const { data, error } = await fetchGetLatestCareerReport(jobId.value);
    if (!error && data) {
      careerReport.value = data;
    } else {
      careerReport.value = null;
    }
  } catch {
    careerReport.value = null;
  } finally {
    loadingCareerReport.value = false;
  }
}

async function handleGenerateMatch() {
  if (!jobId.value) return;
  generatingMatch.value = true;
  window.$message?.info($t('page.jobs.matchAnalyzing') as string, { duration: 5000 });
  try {
    const { data, error } = await fetchGenerateJobStudentMatch(jobId.value);
    if (error || !data) {
      window.$message?.error(extractApiError(error, $t('page.jobs.matchFailed') as string), { duration: 6000 });
      return;
    }
    matchResult.value = data;
    window.$message?.success($t('page.jobs.matchSuccess') as string);
  } catch (error) {
    window.$message?.error(extractApiError(error, $t('page.jobs.matchFailedRetry') as string), { duration: 6000 });
  } finally {
    generatingMatch.value = false;
  }
}

// --- 职业发展报告：生成 / 润色 / 完整性检查 ---
async function refreshJobAnalysisAfterReport() {
  await loadMatchResult();
}

function isPlaceholderDocTitle(title: string): boolean {
  return /^文档\s*#\d+$/.test(title.trim());
}

function parseTitleFromSnippet(snippet?: string): string {
  if (!snippet?.trim()) return '';
  const match = snippet.match(/#\s*([^#·]+?)(?:\s*##|\s*·|$)/);
  return match?.[1]?.trim() || '';
}

function hasValue<T>(value: T | null | undefined): value is T {
  return value !== null && value !== undefined;
}

function knowledgeSourceTitle(src: CareerReportApi.KnowledgeSource, index: number) {
  const meta = (src.metadata ?? {}) as Record<string, unknown>;
  const file = String(meta.documentTitle ?? meta.sourceFile ?? meta.content ?? '').trim();
  const fromFile = file ? file.replace(/\.md$/i, '') : '';
  const candidates = [src.title?.trim(), fromFile, parseTitleFromSnippet(src.snippet)];
  const title = candidates.find(item => item && !isPlaceholderDocTitle(item));
  if (title) {
    return title;
  }
  return hasValue(src.documentId) ? `学习路线 #${src.documentId}` : `来源 ${index + 1}`;
}

function knowledgeSourceSnippet(src: CareerReportApi.KnowledgeSource) {
  const scoreText =
    hasValue(src.score) && !Number.isNaN(Number(src.score)) ? `相似度 ${(Number(src.score) * 100).toFixed(1)}%` : '';
  const chunkText = hasValue(src.chunkIndex) ? `块 #${src.chunkIndex}` : '';
  return src.snippet?.trim() || [scoreText, chunkText].filter(Boolean).join(' · ');
}

function normalizeKnowledgeSource(
  src: CareerReportApi.KnowledgeSource,
  index: number
): { title: string; snippet: string; url?: string } {
  return {
    title: knowledgeSourceTitle(src, index),
    snippet: knowledgeSourceSnippet(src),
    url: src.url
  };
}

const knowledgeSourcesDisplay = computed(() => {
  const raw = careerReport.value?.knowledgeSources;
  if (!Array.isArray(raw)) return [];
  return raw.map((src, i) => normalizeKnowledgeSource(src, i));
});

async function applyCareerReportSuccess(data: CareerReportApi.CareerReport) {
  careerReport.value = data;
  careerReportDrawerVisible.value = true;
  await refreshJobAnalysisAfterReport();
}

function openCareerReportDrawer() {
  careerReportDrawerVisible.value = true;
  // 抽屉打开时刷新一次，避免他端更新
  loadCareerReport();
}

async function handleGenerateCareerReport() {
  if (!jobId.value) return;
  generatingCareerReport.value = true;
  window.$message?.info($t('page.jobs.careerReport.generating') as string, { duration: 6000 });
  try {
    const payload: CareerReportApi.CareerReportGenerateReq = {
      userPreference: careerReportGenerateForm.value.userPreference?.trim() || undefined,
      focus: careerReportGenerateForm.value.focus?.trim() || undefined
    };
    const { data, error } = await fetchGenerateCareerReport(jobId.value, payload);
    if (error || !data) {
      window.$message?.error(extractApiError(error, $t('page.jobs.careerReport.generateFailed') as string), {
        duration: 6000
      });
      return;
    }
    await applyCareerReportSuccess(data);
    window.$message?.success($t('page.jobs.careerReport.generateSuccess') as string);
  } catch (error) {
    window.$message?.error(extractApiError(error, $t('page.jobs.careerReport.generateFailed') as string), {
      duration: 6000
    });
  } finally {
    generatingCareerReport.value = false;
  }
}

async function handlePolishCareerReport() {
  if (!careerReport.value?.id) return;
  polishingCareerReport.value = true;
  window.$message?.info($t('page.jobs.careerReport.polishing') as string, { duration: 5000 });
  try {
    const { data, error } = await fetchPolishCareerReport(careerReport.value.id, {
      instruction: careerReportPolishInstruction.value?.trim() || undefined
    });
    if (error || !data) {
      window.$message?.error(extractApiError(error, $t('page.jobs.careerReport.polishFailed') as string), {
        duration: 6000
      });
      return;
    }
    careerReport.value = data;
    careerReportPolishInstruction.value = '';
    window.$message?.success($t('page.jobs.careerReport.polishSuccess') as string);
  } catch (error) {
    window.$message?.error(extractApiError(error, $t('page.jobs.careerReport.polishFailed') as string), {
      duration: 6000
    });
  } finally {
    polishingCareerReport.value = false;
  }
}

async function handleCheckCareerReportIntegrity() {
  if (!careerReport.value?.id) return;
  checkingCareerReport.value = true;
  window.$message?.info($t('page.jobs.careerReport.checking') as string, { duration: 4000 });
  try {
    const { data, error } = await fetchCheckCareerReportIntegrity(careerReport.value.id);
    if (error || !data) {
      window.$message?.error(extractApiError(error, $t('page.jobs.careerReport.checkFailed') as string), {
        duration: 6000
      });
      return;
    }
    careerReportCheckResult.value = data;
    await loadCareerReport();
    window.$message?.success($t('page.jobs.careerReport.checkSuccess') as string);
  } catch (error) {
    window.$message?.error(extractApiError(error, $t('page.jobs.careerReport.checkFailed') as string), {
      duration: 6000
    });
  } finally {
    checkingCareerReport.value = false;
  }
}

function getCareerReportStatusLabel(status?: number): string {
  switch (status) {
    case 1:
      return $t('page.jobs.careerReport.status.draft') as string;
    case 2:
      return $t('page.jobs.careerReport.status.checked') as string;
    case 3:
      return $t('page.jobs.careerReport.status.published') as string;
    case 4:
      return $t('page.jobs.careerReport.status.checkFailed') as string;
    default:
      return $t('page.jobs.careerReport.status.unknown') as string;
  }
}

function getCareerReportStatusType(status?: number): 'default' | 'info' | 'success' | 'warning' | 'error' {
  switch (status) {
    case 1:
      return 'default';
    case 2:
      return 'success';
    case 3:
      return 'info';
    case 4:
      return 'error';
    default:
      return 'default';
  }
}

function getRiskLabel(level?: string): string {
  switch ((level || '').toLowerCase()) {
    case 'low':
      return $t('page.jobs.careerReport.riskLow') as string;
    case 'medium':
      return $t('page.jobs.careerReport.riskMedium') as string;
    case 'high':
      return $t('page.jobs.careerReport.riskHigh') as string;
    default:
      return level || '-';
  }
}

function handleEdit() {
  router.push({ name: 'job-edit', query: { id: String(jobId.value) } });
}

function handleBack() {
  router.back();
}

// --- 人岗匹配 4 维雷达图数据 ---
const matchDimensions = computed<Dimension[]>(() => {
  const dims = matchResult.value?.dimensions;
  if (!dims) return [];
  const labels: Record<string, string> = {
    basic: $t('page.jobs.matchDimensions.basic'),
    professionalSkill: $t('page.jobs.matchDimensions.professionalSkill'),
    professionalQuality: $t('page.jobs.matchDimensions.professionalQuality'),
    developmentPotential: $t('page.jobs.matchDimensions.developmentPotential')
  };
  return (Object.keys(labels) as Array<keyof typeof labels>).map(key => ({
    key,
    label: labels[key],
    score: dims[key]?.score || 0,
    max: 100
  }));
});

// 报告章节渲染：把 reportContent 里的章节键 -> 内容做平铺
const reportSections = computed<Array<{ key: string; value: any }>>(() => {
  const content = careerReport.value?.reportContent;
  if (!content || typeof content !== 'object') return [];
  return Object.keys(content).map(k => ({ key: k, value: (content as any)[k] }));
});

function renderSectionValue(value: any): string {
  if (value === null || value === undefined) return '-';
  if (typeof value === 'string') return value;
  if (Array.isArray(value)) {
    // 数组：如果是字符串数组直接 join，对象数组 JSON 化
    if (value.every(v => typeof v === 'string' || typeof v === 'number')) {
      return value.map(String).join('\n');
    }
    return JSON.stringify(value, null, 2);
  }
  if (typeof value === 'object') {
    return JSON.stringify(value, null, 2);
  }
  return String(value);
}

// --- 结构化章节数据提取（用于美化渲染）---
interface ActionTask {
  task: string;
  cycle: string;
  deliverable: string;
  verification: string;
}

interface EvaluationMetric {
  metric: string;
  target: string;
  deadline: string;
}

function toEvidenceStringArray(val: unknown): string[] {
  if (Array.isArray(val))
    return val
      .map(String)
      .map(s => s.trim())
      .filter(Boolean);
  if (typeof val === 'string' && val.trim()) return [val.trim()];
  return [];
}

const evidenceData = computed(() => {
  const raw = careerReport.value?.reportContent?.evidence;
  if (raw === null || raw === undefined) return null;

  if (Array.isArray(raw)) {
    const technical = toEvidenceStringArray(raw);
    if (!technical.length) return null;
    return { industry: [] as string[], technical, communication: [] as string[], sourceNote: '' };
  }

  if (typeof raw !== 'object') return null;

  const r = raw as Record<string, unknown>;
  const industry = toEvidenceStringArray(r.industryEvidence);
  const technical = toEvidenceStringArray(r.technicalEvidence);
  const communication = toEvidenceStringArray(r.communicationEvidence);

  if (!technical.length && r.gapAnalysis !== null && r.gapAnalysis !== undefined) {
    technical.push(...toEvidenceStringArray(r.gapAnalysis));
  }
  if (!industry.length && r.actionPlanRationale !== null && r.actionPlanRationale !== undefined) {
    industry.push(...toEvidenceStringArray(r.actionPlanRationale));
  }

  const sourceNote = typeof r.source === 'string' ? r.source.trim() : '';

  if (!industry.length && !technical.length && !communication.length && !sourceNote) return null;

  return { industry, technical, communication, sourceNote };
});

const actionPlanData = computed(() => {
  const raw = careerReport.value?.reportContent?.actionPlan;
  if (!raw || typeof raw !== 'object') return null;
  const mapTasks = (arr: any): ActionTask[] => {
    if (!Array.isArray(arr)) return [];
    return arr.map((t: any) => ({
      task: t?.task || '',
      cycle: t?.cycle || '',
      deliverable: t?.deliverable || '',
      verification: t?.verification || ''
    }));
  };
  return {
    shortTerm: mapTasks(raw.shortTerm),
    midTerm: mapTasks(raw.midTerm)
  };
});

const careerPathData = computed(() => {
  const raw = careerReport.value?.reportContent?.careerPath;
  if (!raw || typeof raw !== 'object') return null;
  return {
    nextStep: (raw.nextStep as string) || '',
    entryPoint: (raw.entryPoint as string) || '',
    transitionLogic: (raw.transitionLogic as string) || '',
    alternativePaths: Array.isArray(raw.alternativePaths) ? (raw.alternativePaths as string[]) : []
  };
});

const careerGoalsData = computed(() => {
  const raw = careerReport.value?.reportContent?.careerGoals;
  if (!raw || typeof raw !== 'object') return null;
  return {
    shortTerm: (raw.shortTerm as string) || '',
    midTerm: (raw.midTerm as string) || '',
    longTerm: (raw.longTerm as string) || ''
  };
});

const evaluationPlanData = computed(() => {
  const raw = careerReport.value?.reportContent?.evaluationPlan;
  if (!raw || typeof raw !== 'object') return null;

  const cycles: string[] = [];
  if (Array.isArray((raw as any).evaluationCycle)) {
    cycles.push(...(raw as any).evaluationCycle.map(String).filter(Boolean));
  } else if (typeof (raw as any).cycle === 'string' && (raw as any).cycle.trim()) {
    cycles.push((raw as any).cycle.trim());
  } else if (Array.isArray((raw as any).cycles)) {
    cycles.push(...(raw as any).cycles.map(String).filter(Boolean));
  }

  const rawMetrics = (raw as any).quantitativeMetrics ?? (raw as any).metrics;
  const metrics: EvaluationMetric[] = Array.isArray(rawMetrics)
    ? rawMetrics
        .map((m: any) => {
          if (typeof m === 'string') {
            return { metric: m.trim(), target: '', deadline: '' };
          }
          return {
            metric: String(m?.metric ?? m?.name ?? '').trim(),
            target: String(m?.target ?? m?.goal ?? '').trim(),
            deadline: String(m?.deadline ?? m?.dueDate ?? '').trim()
          };
        })
        .filter((m: EvaluationMetric) => m.metric || m.target || m.deadline)
    : [];

  const qualitative: string[] = Array.isArray((raw as any).qualitativeAssessment)
    ? (raw as any).qualitativeAssessment.map(String).filter(Boolean)
    : [];

  if (!cycles.length && !metrics.length && !qualitative.length) return null;

  const metricsStructured = metrics.some(m => m.target || m.deadline);

  return { cycles, metrics, qualitative, metricsStructured };
});

const careerExplorationData = computed(() => {
  const raw = careerReport.value?.reportContent?.careerExploration;
  if (!raw || typeof raw !== 'object') return null;
  return {
    roleClarity: (raw.roleClarity as string) || '',
    industryInsight: (raw.industryInsight as string) || '',
    marketPositioning: (raw.marketPositioning as string) || ''
  };
});

const KNOWN_SECTION_KEYS = new Set([
  'evidence',
  'actionPlan',
  'careerPath',
  'careerGoals',
  'evaluationPlan',
  'careerExploration'
]);

const unknownSections = computed(() => {
  return reportSections.value.filter(s => !KNOWN_SECTION_KEYS.has(s.key));
});

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
    <div class="h-full min-h-[500px] bg-slate-50/50 p-6 dark:bg-dark-100">
      <!-- 头部操作栏 -->
      <div class="mb-6 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <NButton quaternary circle @click="handleBack">
            <template #icon><span>←</span></template>
          </NButton>
          <h1 class="text-2xl text-slate-800 font-bold dark:text-gray-200">{{ $t('page.jobs.viewDetail') }}</h1>
        </div>
        <div class="flex gap-3">
          <NButton :loading="generatingMatch" @click="handleGenerateMatch">
            <template #icon><span>🎯</span></template>
            {{ matchResult ? $t('page.jobs.reanalyzeMatch') : $t('page.jobs.analyzeMatch') }}
          </NButton>
          <NButton type="info" ghost :loading="loadingCareerReport" @click="openCareerReportDrawer">
            <template #icon><span>📑</span></template>
            {{ careerReport ? $t('page.jobs.careerReport.view') : $t('page.jobs.careerReport.entry') }}
          </NButton>
          <NButton type="primary" @click="handleEdit">
            <template #icon><span>✏️</span></template>
            {{ $t('page.jobs.edit') }}
          </NButton>
        </div>
      </div>

      <!-- 人岗匹配评分卡 -->
      <div v-if="matchResult" class="mb-6">
        <CapabilityScoreCard
          :dimensions="matchDimensions"
          :total-score="matchResult.overallScore || 0"
          :total-max="100"
          :total-label="$t('page.jobs.overallMatchScore')"
          :summary-label="$t('page.jobs.matchSummaryLabel')"
          :summary="matchResult.summary"
          :strengths-label="$t('page.jobs.matchHighlightsLabel')"
          :strengths="matchResult.matchedHighlights"
        />
      </div>

      <div v-if="jobDetail" class="space-y-6">
        <!-- 基本信息 -->
        <NCard :title="$t('page.jobs.basicInfo')" class="rounded-xl">
          <div class="space-y-4">
            <div class="flex items-center justify-between border-b border-slate-100 py-2 dark:border-gray-700">
              <span class="text-slate-600 dark:text-gray-400">{{ $t('page.jobs.jobName') }}</span>
              <span class="text-slate-800 font-semibold dark:text-gray-200">{{ jobDetail.jobName }}</span>
            </div>
            <div class="flex items-center justify-between border-b border-slate-100 py-2 dark:border-gray-700">
              <span class="text-slate-600 dark:text-gray-400">{{ $t('page.jobs.companyName') }}</span>
              <span class="text-slate-800 font-semibold dark:text-gray-200">{{ jobDetail.companyName }}</span>
            </div>
            <div class="flex items-center justify-between border-b border-slate-100 py-2 dark:border-gray-700">
              <span class="text-slate-600 dark:text-gray-400">{{ $t('page.jobs.location') }}</span>
              <span class="text-slate-800 font-semibold dark:text-gray-200">
                {{ formatJobLocation(jobDetail.location) }}
              </span>
            </div>
            <div
              v-if="normalizeJobLink(jobDetail.link)"
              class="flex items-center justify-between gap-4 border-b border-slate-100 py-2 dark:border-gray-700"
            >
              <span class="shrink-0 text-slate-600 dark:text-gray-400">{{ $t('page.jobs.jobLink') }}</span>
              <a
                :href="normalizeJobLink(jobDetail.link)!"
                target="_blank"
                rel="noopener noreferrer"
                class="text-right text-blue-600 font-semibold hover:underline"
              >
                {{ $t('page.jobs.jobLink') }}
              </a>
            </div>
            <div class="flex items-center justify-between py-2">
              <span class="text-slate-600 dark:text-gray-400">{{ $t('page.jobs.salaryInfo') }}</span>
              <span class="text-orange-600 font-semibold">{{ formatSalary(jobDetail) }}</span>
            </div>
          </div>
        </NCard>

        <!-- 岗位描述 -->
        <NCard :title="$t('page.jobs.jobDescription')" class="rounded-xl">
          <p class="whitespace-pre-wrap text-slate-700 leading-relaxed dark:text-gray-300">
            {{ jobDetail.description }}
          </p>
        </NCard>

        <!-- 岗位关键词 -->
        <NCard
          v-if="jobDetail.keywords && jobDetail.keywords.length > 0"
          :title="$t('page.jobs.jobKeywords')"
          class="rounded-xl"
        >
          <div class="flex flex-wrap gap-2">
            <NTag v-for="keyword in jobDetail.keywords" :key="keyword" type="info" round>
              {{ keyword }}
            </NTag>
          </div>
        </NCard>

        <!-- 公司信息 -->
        <NCard
          v-if="jobDetail.companyIndustries"
          :title="$t('page.jobs.companyIndustry') + $t('page.jobs.infoSuffix')"
          class="rounded-xl"
        >
          <div class="space-y-3">
            <div v-if="jobDetail.companyIndustries.length > 0">
              <span class="text-slate-600 dark:text-gray-400">{{ $t('page.jobs.industryLabel') }}</span>
              <NTag v-for="industry in jobDetail.companyIndustries" :key="industry" type="default" class="ml-2">
                {{ industry }}
              </NTag>
            </div>
          </div>
        </NCard>
      </div>
    </div>

    <!-- 职业发展报告 抽屉 -->
    <NDrawer v-model:show="careerReportDrawerVisible" :width="720" :auto-focus="false" placement="right">
      <NDrawerContent :title="$t('page.jobs.careerReport.drawerTitle')" closable>
        <!-- 顶部：状态条 + 操作 -->
        <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div class="flex flex-wrap items-center gap-2">
            <template v-if="careerReport">
              <NTag :type="getCareerReportStatusType(careerReport.status)" round size="small">
                {{ $t('page.jobs.careerReport.statusLabel') }}：{{ getCareerReportStatusLabel(careerReport.status) }}
              </NTag>
              <NTag
                v-if="careerReport.completenessScore !== null && careerReport.completenessScore !== undefined"
                type="warning"
                round
                size="small"
              >
                {{ $t('page.jobs.careerReport.completenessScore') }}：{{ careerReport.completenessScore }}/100
              </NTag>
            </template>
          </div>
          <div class="flex gap-2">
            <NButton size="small" type="primary" :loading="generatingCareerReport" @click="handleGenerateCareerReport">
              <template #icon>
                <span>{{ careerReport ? '🔄' : '✨' }}</span>
              </template>
              {{ careerReport ? $t('page.jobs.careerReport.regenerate') : $t('page.jobs.careerReport.generate') }}
            </NButton>
            <NButton
              v-if="careerReport"
              size="small"
              :loading="checkingCareerReport"
              @click="handleCheckCareerReportIntegrity"
            >
              <template #icon><span>✅</span></template>
              {{ $t('page.jobs.careerReport.check') }}
            </NButton>
          </div>
        </div>

        <!-- 生成参数表单 -->
        <NCard
          v-if="!careerReport"
          size="small"
          :bordered="false"
          class="mb-4 rounded-lg bg-blue-50/40 dark:bg-blue-900/10"
        >
          <NEmpty :description="$t('page.jobs.careerReport.empty')" class="py-2" />
        </NCard>

        <!-- 生成偏好（生成 / 重新生成 都可填）-->
        <NCard
          size="small"
          :title="$t('page.jobs.careerReport.userPreferenceLabel') + ' / ' + $t('page.jobs.careerReport.focusLabel')"
          class="mb-4 rounded-lg"
        >
          <div class="space-y-2">
            <NInput
              v-model:value="careerReportGenerateForm.userPreference"
              type="textarea"
              :placeholder="$t('page.jobs.careerReport.userPreferencePlaceholder') as string"
              :autosize="{ minRows: 1, maxRows: 3 }"
            />
            <NInput
              v-model:value="careerReportGenerateForm.focus"
              type="textarea"
              :placeholder="$t('page.jobs.careerReport.focusPlaceholder') as string"
              :autosize="{ minRows: 1, maxRows: 3 }"
            />
          </div>
        </NCard>

        <!-- 完整性检查结果（即时显示） -->
        <NCard
          v-if="careerReportCheckResult"
          size="small"
          :title="$t('page.jobs.careerReport.check')"
          class="mb-4 rounded-lg"
        >
          <div class="text-sm space-y-2">
            <div class="flex flex-wrap items-center gap-2">
              <NTag :type="careerReportCheckResult.passed ? 'success' : 'error'" size="small">
                {{ careerReportCheckResult.passed ? '✅ 通过' : '❌ 未通过' }}
              </NTag>
              <NTag
                v-if="
                  careerReportCheckResult.completenessScore !== null &&
                  careerReportCheckResult.completenessScore !== undefined
                "
                type="warning"
                size="small"
              >
                {{ $t('page.jobs.careerReport.completenessScore') }}：{{
                  careerReportCheckResult.completenessScore
                }}/100
              </NTag>
              <NTag v-if="careerReportCheckResult.riskLevel" size="small">
                {{ $t('page.jobs.careerReport.riskLevel') }}：{{ getRiskLabel(careerReportCheckResult.riskLevel) }}
              </NTag>
            </div>
            <div v-if="careerReportCheckResult.missingSections && careerReportCheckResult.missingSections.length">
              <span class="font-medium">{{ $t('page.jobs.careerReport.missingSections') }}：</span>
              <NTag
                v-for="s in careerReportCheckResult.missingSections"
                :key="'miss-' + s"
                size="small"
                type="warning"
                class="ml-1"
              >
                {{ s }}
              </NTag>
            </div>
            <div v-if="careerReportCheckResult.nonActionableItems && careerReportCheckResult.nonActionableItems.length">
              <span class="font-medium">{{ $t('page.jobs.careerReport.nonActionableItems') }}：</span>
              <ul class="list-disc list-inside text-slate-700 dark:text-gray-300">
                <li v-for="(it, i) in careerReportCheckResult.nonActionableItems" :key="'nai-' + i">{{ it }}</li>
              </ul>
            </div>
            <div v-if="careerReportCheckResult.weakEvidenceItems && careerReportCheckResult.weakEvidenceItems.length">
              <span class="font-medium">{{ $t('page.jobs.careerReport.weakEvidenceItems') }}：</span>
              <ul class="list-disc list-inside text-slate-700 dark:text-gray-300">
                <li v-for="(it, i) in careerReportCheckResult.weakEvidenceItems" :key="'wei-' + i">{{ it }}</li>
              </ul>
            </div>
          </div>
        </NCard>

        <!-- 报告正文（结构化渲染）-->
        <template v-if="careerReport">
          <!-- evidence：三列证据卡片 -->
          <NCard v-if="evidenceData" size="small" title="📋 证据支撑" class="mb-4 rounded-lg">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
              <div class="border border-blue-100 rounded-lg bg-blue-50 p-3 dark:border-blue-800/40 dark:bg-blue-900/20">
                <div class="mb-2 flex items-center gap-1.5 text-sm text-blue-700 font-semibold dark:text-blue-400">
                  <span>🏭</span>
                  行业证据
                </div>
                <ul class="space-y-1.5">
                  <li
                    v-for="(item, i) in evidenceData.industry"
                    :key="'ind-' + i"
                    class="flex items-start gap-1.5 text-xs text-slate-700 dark:text-gray-300"
                  >
                    <span class="mt-0.5 shrink-0 text-blue-400">◆</span>
                    <span>{{ item }}</span>
                  </li>
                </ul>
              </div>
              <div
                class="border border-emerald-100 rounded-lg bg-emerald-50 p-3 dark:border-emerald-800/40 dark:bg-emerald-900/20"
              >
                <div
                  class="mb-2 flex items-center gap-1.5 text-sm text-emerald-700 font-semibold dark:text-emerald-400"
                >
                  <span>💻</span>
                  技术证据
                </div>
                <ul class="space-y-1.5">
                  <li
                    v-for="(item, i) in evidenceData.technical"
                    :key="'tec-' + i"
                    class="flex items-start gap-1.5 text-xs text-slate-700 dark:text-gray-300"
                  >
                    <span class="mt-0.5 shrink-0 text-emerald-400">◆</span>
                    <span>{{ item }}</span>
                  </li>
                </ul>
              </div>
              <div
                class="border border-violet-100 rounded-lg bg-violet-50 p-3 dark:border-violet-800/40 dark:bg-violet-900/20"
              >
                <div class="mb-2 flex items-center gap-1.5 text-sm text-violet-700 font-semibold dark:text-violet-400">
                  <span>🗣️</span>
                  沟通证据
                </div>
                <ul class="space-y-1.5">
                  <li
                    v-for="(item, i) in evidenceData.communication"
                    :key="'com-' + i"
                    class="flex items-start gap-1.5 text-xs text-slate-700 dark:text-gray-300"
                  >
                    <span class="mt-0.5 shrink-0 text-violet-400">◆</span>
                    <span>{{ item }}</span>
                  </li>
                </ul>
              </div>
            </div>
            <p
              v-if="evidenceData.sourceNote"
              class="mt-3 border-t border-slate-200 pt-3 text-xs text-slate-500 dark:border-gray-700 dark:text-gray-400"
            >
              {{ evidenceData.sourceNote }}
            </p>
          </NCard>

          <!-- actionPlan：短期 / 中期行动计划 -->
          <NCard v-if="actionPlanData" size="small" title="📅 行动计划" class="mb-4 rounded-lg">
            <!-- 短期计划 -->
            <div v-if="actionPlanData.shortTerm.length" class="mb-4">
              <div class="mb-3 flex items-center gap-2">
                <span class="text-base text-orange-500">🎯</span>
                <span class="text-slate-800 font-semibold dark:text-gray-200">短期计划</span>
                <NTag size="tiny" type="warning" round>近期执行</NTag>
              </div>
              <div class="space-y-3">
                <div
                  v-for="(t, i) in actionPlanData.shortTerm"
                  :key="'st-' + i"
                  class="border border-orange-100 rounded-lg bg-orange-50/60 p-3 dark:border-orange-800/30 dark:bg-orange-900/10"
                >
                  <div class="mb-2 flex items-start gap-2">
                    <span
                      class="mt-0.5 h-5 w-5 flex shrink-0 items-center justify-center rounded-full bg-orange-500 text-xs text-white font-bold"
                    >
                      {{ i + 1 }}
                    </span>
                    <span class="text-sm text-slate-800 font-medium dark:text-gray-200">{{ t.task }}</span>
                  </div>
                  <div class="grid grid-cols-1 ml-7 gap-2 sm:grid-cols-3">
                    <div class="flex items-center gap-1 text-xs text-slate-600 dark:text-gray-400">
                      <span class="text-orange-400">⏱️</span>
                      <span class="font-medium">周期：</span>
                      {{ t.cycle }}
                    </div>
                    <div class="flex items-center gap-1 text-xs text-slate-600 dark:text-gray-400">
                      <span class="text-orange-400">📦</span>
                      <span class="font-medium">交付物：</span>
                      {{ t.deliverable }}
                    </div>
                    <div class="flex items-center gap-1 text-xs text-slate-600 dark:text-gray-400">
                      <span class="text-orange-400">✅</span>
                      <span class="font-medium">验证：</span>
                      {{ t.verification }}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 中期计划 -->
            <div v-if="actionPlanData.midTerm.length">
              <div class="mb-3 flex items-center gap-2">
                <span class="text-base text-blue-500">🚀</span>
                <span class="text-slate-800 font-semibold dark:text-gray-200">中期计划</span>
                <NTag size="tiny" type="info" round>持续成长</NTag>
              </div>
              <div class="space-y-3">
                <div
                  v-for="(t, i) in actionPlanData.midTerm"
                  :key="'mt-' + i"
                  class="border border-blue-100 rounded-lg bg-blue-50/60 p-3 dark:border-blue-800/30 dark:bg-blue-900/10"
                >
                  <div class="mb-2 flex items-start gap-2">
                    <span
                      class="mt-0.5 h-5 w-5 flex shrink-0 items-center justify-center rounded-full bg-blue-500 text-xs text-white font-bold"
                    >
                      {{ i + 1 }}
                    </span>
                    <span class="text-sm text-slate-800 font-medium dark:text-gray-200">{{ t.task }}</span>
                  </div>
                  <div class="grid grid-cols-1 ml-7 gap-2 sm:grid-cols-3">
                    <div class="flex items-center gap-1 text-xs text-slate-600 dark:text-gray-400">
                      <span class="text-blue-400">⏱️</span>
                      <span class="font-medium">周期：</span>
                      {{ t.cycle }}
                    </div>
                    <div class="flex items-center gap-1 text-xs text-slate-600 dark:text-gray-400">
                      <span class="text-blue-400">📦</span>
                      <span class="font-medium">交付物：</span>
                      {{ t.deliverable }}
                    </div>
                    <div class="flex items-center gap-1 text-xs text-slate-600 dark:text-gray-400">
                      <span class="text-blue-400">✅</span>
                      <span class="font-medium">验证：</span>
                      {{ t.verification }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </NCard>

          <!-- careerPath：职业路径 -->
          <NCard v-if="careerPathData" size="small" title="🗺️ 职业路径" class="mb-4 rounded-lg">
            <!-- 入口 → 下一步 -->
            <div class="mb-4 flex flex-col items-center gap-2 sm:flex-row sm:gap-4">
              <div
                class="min-w-[140px] border border-indigo-200 rounded-lg bg-indigo-50 px-4 py-2.5 text-center dark:border-indigo-800/40 dark:bg-indigo-900/20"
              >
                <div class="mb-0.5 text-xs text-indigo-500 font-medium dark:text-indigo-400">🏭 入职起点</div>
                <div class="text-sm text-slate-800 font-semibold dark:text-gray-200">
                  {{ careerPathData.entryPoint }}
                </div>
              </div>
              <div class="rotate-90 transform text-xl text-indigo-400 sm:rotate-0">→</div>
              <div
                class="min-w-[140px] border border-indigo-200 rounded-lg bg-indigo-50 px-4 py-2.5 text-center dark:border-indigo-800/40 dark:bg-indigo-900/20"
              >
                <div class="mb-0.5 text-xs text-indigo-500 font-medium dark:text-indigo-400">🚀 下一步发展</div>
                <div class="text-sm text-slate-800 font-semibold dark:text-gray-200">{{ careerPathData.nextStep }}</div>
              </div>
            </div>

            <!-- 过渡逻辑 -->
            <div class="mb-3 rounded-lg bg-slate-50 p-3 dark:bg-gray-800/40">
              <div class="mb-1.5 flex items-center gap-1.5 text-xs text-slate-600 font-semibold dark:text-gray-400">
                <span>💡</span>
                过渡逻辑
              </div>
              <p class="text-sm text-slate-700 leading-relaxed dark:text-gray-300">
                {{ careerPathData.transitionLogic }}
              </p>
            </div>

            <!-- 备选路径 -->
            <div v-if="careerPathData.alternativePaths.length">
              <div class="mb-2 flex items-center gap-1.5 text-xs text-slate-600 font-semibold dark:text-gray-400">
                <span>🔄</span>
                备选路径
              </div>
              <div class="flex flex-wrap gap-2">
                <NTag
                  v-for="(alt, i) in careerPathData.alternativePaths"
                  :key="'alt-' + i"
                  type="default"
                  round
                  size="small"
                >
                  {{ alt }}
                </NTag>
              </div>
            </div>
          </NCard>

          <!-- careerGoals：职业目标（时间线） -->
          <NCard v-if="careerGoalsData" size="small" title="🎯 职业目标" class="mb-4 rounded-lg">
            <div class="relative">
              <!-- 时间线竖线 -->
              <div
                class="absolute bottom-2 left-[11px] top-2 w-0.5 from-amber-400 via-rose-400 to-violet-500 bg-gradient-to-b"
              ></div>
              <div class="space-y-4">
                <!-- 短期 -->
                <div v-if="careerGoalsData.shortTerm" class="relative flex items-start gap-3">
                  <span
                    class="relative z-10 h-6 w-6 flex shrink-0 items-center justify-center rounded-full bg-amber-500 text-xs text-white font-bold shadow"
                  >
                    1
                  </span>
                  <div
                    class="flex-1 border border-amber-100 rounded-lg bg-amber-50 p-3 dark:border-amber-800/30 dark:bg-amber-900/10"
                  >
                    <div class="mb-1 flex items-center gap-2">
                      <span class="text-sm text-slate-800 font-semibold dark:text-gray-200">短期目标</span>
                      <NTag size="tiny" type="warning" round>6个月内</NTag>
                    </div>
                    <p class="text-sm text-slate-700 leading-relaxed dark:text-gray-300">
                      {{ careerGoalsData.shortTerm }}
                    </p>
                  </div>
                </div>
                <!-- 中期 -->
                <div v-if="careerGoalsData.midTerm" class="relative flex items-start gap-3">
                  <span
                    class="relative z-10 h-6 w-6 flex shrink-0 items-center justify-center rounded-full bg-rose-500 text-xs text-white font-bold shadow"
                  >
                    2
                  </span>
                  <div
                    class="flex-1 border border-rose-100 rounded-lg bg-rose-50 p-3 dark:border-rose-800/30 dark:bg-rose-900/10"
                  >
                    <div class="mb-1 flex items-center gap-2">
                      <span class="text-sm text-slate-800 font-semibold dark:text-gray-200">中期目标</span>
                      <NTag size="tiny" type="error" round>2年内</NTag>
                    </div>
                    <p class="text-sm text-slate-700 leading-relaxed dark:text-gray-300">
                      {{ careerGoalsData.midTerm }}
                    </p>
                  </div>
                </div>
                <!-- 长期 -->
                <div v-if="careerGoalsData.longTerm" class="relative flex items-start gap-3">
                  <span
                    class="relative z-10 h-6 w-6 flex shrink-0 items-center justify-center rounded-full bg-violet-500 text-xs text-white font-bold shadow"
                  >
                    3
                  </span>
                  <div
                    class="flex-1 border border-violet-100 rounded-lg bg-violet-50 p-3 dark:border-violet-800/30 dark:bg-violet-900/10"
                  >
                    <div class="mb-1 flex items-center gap-2">
                      <span class="text-sm text-slate-800 font-semibold dark:text-gray-200">长期目标</span>
                      <NTag size="tiny" type="info" round>5年内</NTag>
                    </div>
                    <p class="text-sm text-slate-700 leading-relaxed dark:text-gray-300">
                      {{ careerGoalsData.longTerm }}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </NCard>

          <!-- evaluationPlan：评估计划 -->
          <NCard v-if="evaluationPlanData" size="small" title="📊 评估计划" class="mb-4 rounded-lg">
            <!-- 评估周期 -->
            <div v-if="evaluationPlanData.cycles.length" class="mb-4">
              <div class="mb-2 flex items-center gap-1.5 text-xs text-slate-600 font-semibold dark:text-gray-400">
                <span>📅</span>
                评估周期
              </div>
              <div class="flex flex-wrap gap-2">
                <NTag v-for="(c, i) in evaluationPlanData.cycles" :key="'cyc-' + i" type="success" round size="small">
                  {{ c }}
                </NTag>
              </div>
            </div>

            <!-- 量化指标 -->
            <div v-if="evaluationPlanData.metrics.length">
              <div class="mb-2 flex items-center gap-1.5 text-xs text-slate-600 font-semibold dark:text-gray-400">
                <span>📈</span>
                量化指标
              </div>
              <!-- LLM 返回字符串数组时：目标/截止已写在描述句内，用列表展示 -->
              <ul
                v-if="!evaluationPlanData.metricsStructured"
                class="list-disc pl-5 text-sm text-slate-700 space-y-2 dark:text-gray-300"
              >
                <li v-for="(m, i) in evaluationPlanData.metrics" :key="'met-li-' + i">{{ m.metric }}</li>
              </ul>
              <div v-else class="overflow-x-auto">
                <table class="w-full border-collapse text-xs">
                  <thead>
                    <tr class="bg-slate-100 dark:bg-gray-800/60">
                      <th class="w-[50%] rounded-l-lg p-2.5 text-left text-slate-700 font-semibold dark:text-gray-300">
                        指标
                      </th>
                      <th class="w-[20%] p-2.5 text-center text-slate-700 font-semibold dark:text-gray-300">目标值</th>
                      <th class="w-[30%] rounded-r-lg p-2.5 text-right text-slate-700 font-semibold dark:text-gray-300">
                        截止时间
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="(m, i) in evaluationPlanData.metrics"
                      :key="'met-' + i"
                      class="border-b border-slate-100 dark:border-gray-700/50 last:border-none"
                    >
                      <td class="p-2.5 text-slate-700 dark:text-gray-300">{{ m.metric }}</td>
                      <td class="p-2.5 text-center">
                        <NTag
                          v-if="m.target"
                          size="tiny"
                          :type="m.target === '100%' || m.target === '达成' ? 'success' : 'warning'"
                          round
                        >
                          {{ m.target }}
                        </NTag>
                        <span v-else class="text-slate-400">-</span>
                      </td>
                      <td class="p-2.5 text-right text-slate-600 dark:text-gray-400">{{ m.deadline || '-' }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- 定性评估 -->
            <div v-if="evaluationPlanData.qualitative.length" class="mt-4">
              <div class="mb-2 flex items-center gap-1.5 text-xs text-slate-600 font-semibold dark:text-gray-400">
                <span>📝</span>
                定性评估
              </div>
              <ul class="list-disc pl-5 text-sm text-slate-700 space-y-2 dark:text-gray-300">
                <li v-for="(q, i) in evaluationPlanData.qualitative" :key="'qual-' + i">{{ q }}</li>
              </ul>
            </div>
          </NCard>

          <!-- careerExploration：职业探索洞察 -->
          <NCard v-if="careerExplorationData" size="small" title="🔍 职业探索" class="mb-4 rounded-lg">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-3">
              <div class="border border-cyan-100 rounded-lg bg-cyan-50 p-3 dark:border-cyan-800/40 dark:bg-cyan-900/20">
                <div class="mb-2 flex items-center gap-1.5 text-sm text-cyan-700 font-semibold dark:text-cyan-400">
                  <span>💼</span>
                  角色定位
                </div>
                <p class="text-xs text-slate-700 leading-relaxed dark:text-gray-300">
                  {{ careerExplorationData.roleClarity }}
                </p>
              </div>
              <div class="border border-teal-100 rounded-lg bg-teal-50 p-3 dark:border-teal-800/40 dark:bg-teal-900/20">
                <div class="mb-2 flex items-center gap-1.5 text-sm text-teal-700 font-semibold dark:text-teal-400">
                  <span>🏭</span>
                  行业洞察
                </div>
                <p class="text-xs text-slate-700 leading-relaxed dark:text-gray-300">
                  {{ careerExplorationData.industryInsight }}
                </p>
              </div>
              <div
                class="border border-purple-100 rounded-lg bg-purple-50 p-3 dark:border-purple-800/40 dark:bg-purple-900/20"
              >
                <div class="mb-2 flex items-center gap-1.5 text-sm text-purple-700 font-semibold dark:text-purple-400">
                  <span>📈</span>
                  市场定位
                </div>
                <p class="text-xs text-slate-700 leading-relaxed dark:text-gray-300">
                  {{ careerExplorationData.marketPositioning }}
                </p>
              </div>
            </div>
          </NCard>

          <!-- 未知章节（回退到 pre/JSON 渲染）-->
          <NCard
            v-if="unknownSections.length"
            size="small"
            :title="$t('page.jobs.careerReport.reportContent')"
            class="mb-4 rounded-lg"
          >
            <div class="space-y-4">
              <div v-for="sec in unknownSections" :key="sec.key">
                <h4 class="mb-1 text-slate-800 font-semibold dark:text-gray-200">{{ sec.key }}</h4>
                <pre
                  class="whitespace-pre-wrap break-words rounded bg-slate-50/60 p-3 text-sm text-slate-700 dark:bg-gray-800/40 dark:text-gray-300"
                  >{{ renderSectionValue(sec.value) }}</pre
                >
              </div>
            </div>
          </NCard>
        </template>

        <!-- 知识来源 -->
        <NCard
          v-if="knowledgeSourcesDisplay.length"
          size="small"
          :title="$t('page.jobs.careerReport.knowledgeSources')"
          class="mb-4 rounded-lg"
        >
          <ul class="text-sm space-y-2">
            <li v-for="(src, i) in knowledgeSourcesDisplay" :key="'ks-' + i" class="text-slate-700 dark:text-gray-300">
              <a
                v-if="src.url"
                :href="src.url"
                target="_blank"
                rel="noopener noreferrer"
                class="text-blue-500 font-medium hover:underline"
              >
                {{ src.title }}
              </a>
              <span v-else class="font-medium">{{ src.title }}</span>
              <span v-if="src.snippet" class="ml-2 text-slate-500 dark:text-gray-400">— {{ src.snippet }}</span>
            </li>
          </ul>
        </NCard>

        <!-- 智能润色 -->
        <NCard v-if="careerReport" size="small" :title="$t('page.jobs.careerReport.polish')" class="mb-4 rounded-lg">
          <div class="space-y-2">
            <NInput
              v-model:value="careerReportPolishInstruction"
              type="textarea"
              :placeholder="$t('page.jobs.careerReport.polishInstructionPlaceholder') as string"
              :autosize="{ minRows: 2, maxRows: 4 }"
            />
            <div class="flex justify-end">
              <NButton size="small" type="primary" :loading="polishingCareerReport" @click="handlePolishCareerReport">
                <template #icon><span>🪄</span></template>
                {{ $t('page.jobs.careerReport.confirmPolish') }}
              </NButton>
            </div>
          </div>
        </NCard>
      </NDrawerContent>
    </NDrawer>
  </NSpin>
</template>

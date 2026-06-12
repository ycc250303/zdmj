<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { $t } from '@/locales';
import {
  fetchGetJobDetail,
  fetchGetJobCapabilityProfile,
  fetchGenerateJobCapabilityProfile,
  fetchGetJobCareerGraph,
  fetchGenerateJobCareerGraph,
  type JobApi
} from '@/service/api/job';
import {
  fetchGetJobStudentMatch,
  fetchGenerateJobStudentMatch,
  type MatchApi
} from '@/service/api/match';
import {
  fetchGetLatestCareerReport,
  fetchGenerateCareerReport,
  fetchPolishCareerReport,
  fetchCheckCareerReportIntegrity,
  type CareerReportApi
} from '@/service/api/career-report';
import CapabilityScoreCard, { type Dimension } from '@/components/common/CapabilityScoreCard.vue';

import { parseApiErrorBody } from '@/service/request/api-error';

const router = useRouter();
const route = useRoute();

const jobId = computed(() => {
  const id = route.query.id;
  return id ? Number(id) : null;
});

const jobDetail = ref<JobApi.JobListItem | null>(null);
const capabilityProfile = ref<JobApi.JobCapabilityProfile | null>(null);
const matchResult = ref<MatchApi.JobStudentMatch | null>(null);
const loading = ref(false);
const generatingProfile = ref(false);
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

// --- 岗位关联图谱相关状态 ---
const careerGraph = ref<JobApi.JobCareerGraph | null>(null);
const loadingCareerGraph = ref(false);
const generatingCareerGraph = ref(false);
const careerGraphDrawerVisible = ref(false);

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
    return `${code}${msg}（请稍后重试，或确认后端 LLM 服务可用、能力画像已生成）`;
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
      // 加载能力画像
      loadCapabilityProfile();
      // 加载人岗匹配结果
      loadMatchResult();
      // 加载职业发展报告（不存在则为 null，不弹错）
      loadCareerReport();
      // 加载岗位关联图谱（不存在则为 null，不弹错）
      loadCareerGraph();
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
  if (!jobId.value) return;

  try {
    const { data, error } = await fetchGetJobCapabilityProfile(jobId.value);
    if (!error && data) {
      capabilityProfile.value = data;
    }
  } catch (err) {
    console.error($t('page.jobs.loadProfileFailed'), err);
  }
}

async function loadMatchResult() {
  if (!jobId.value) return;
  try {
    const { data, error } = await fetchGetJobStudentMatch(jobId.value);
    if (!error && data) {
      matchResult.value = data;
    }
  } catch (err) {
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
  } catch (err) {
    careerReport.value = null;
  } finally {
    loadingCareerReport.value = false;
  }
}

async function loadCareerGraph() {
  if (!jobId.value) return;
  loadingCareerGraph.value = true;
  try {
    const { data, error } = await fetchGetJobCareerGraph(jobId.value);
    if (!error && data) {
      careerGraph.value = data;
    } else {
      careerGraph.value = null;
    }
  } catch {
    careerGraph.value = null;
  } finally {
    loadingCareerGraph.value = false;
  }
}

function openCareerGraphDrawer() {
  careerGraphDrawerVisible.value = true;
  loadCareerGraph();
}

async function pollLatestCareerGraph(
  totalMs = 90_000,
  intervalMs = 5_000
): Promise<JobApi.JobCareerGraph | null> {
  if (!jobId.value) return null;
  const start = Date.now();
  while (Date.now() - start < totalMs) {
    await new Promise(r => setTimeout(r, intervalMs));
    try {
      const { data, error } = await fetchGetJobCareerGraph(jobId.value);
      if (!error && data) {
        return data;
      }
    } catch {
      // 单次失败忽略，继续轮询
    }
  }
  return null;
}

async function handleGenerateCareerGraph() {
  if (!jobId.value) return;
  generatingCareerGraph.value = true;
  window.$message?.info($t('page.jobs.careerGraph.generating') as string, { duration: 6000 });
  try {
    const { data, error } = await fetchGenerateJobCareerGraph(jobId.value);
    if (!error && data) {
      careerGraph.value = data;
      careerGraphDrawerVisible.value = true;
      window.$message?.success($t('page.jobs.careerGraph.generateSuccess') as string);
      return;
    }
    if (isGatewayTimeout(error)) {
      window.$message?.warning($t('page.jobs.careerGraph.gatewayTimeoutFallback') as string, { duration: 6000 });
      const polled = await pollLatestCareerGraph(120_000, 5_000);
      if (polled) {
        careerGraph.value = polled;
        careerGraphDrawerVisible.value = true;
        window.$message?.success($t('page.jobs.careerGraph.generateSuccess') as string);
      } else {
        window.$message?.error($t('page.jobs.careerGraph.gatewayTimeoutHint') as string, { duration: 8000 });
      }
    } else {
      window.$message?.error(
        extractApiError(error, $t('page.jobs.careerGraph.generateFailed') as string),
        { duration: 6000 }
      );
    }
  } catch (err) {
    if (isGatewayTimeout(err)) {
      window.$message?.warning($t('page.jobs.careerGraph.gatewayTimeoutFallback') as string, { duration: 6000 });
      const polled = await pollLatestCareerGraph(120_000, 5_000);
      if (polled) {
        careerGraph.value = polled;
        careerGraphDrawerVisible.value = true;
        window.$message?.success($t('page.jobs.careerGraph.generateSuccess') as string);
      } else {
        window.$message?.error($t('page.jobs.careerGraph.gatewayTimeoutHint') as string, { duration: 8000 });
      }
    } else {
      window.$message?.error(
        extractApiError(err, $t('page.jobs.careerGraph.generateFailed') as string),
        { duration: 6000 }
      );
    }
  } finally {
    generatingCareerGraph.value = false;
  }
}

function getTransitionDifficultyLabel(difficulty?: string): string {
  switch ((difficulty || '').toLowerCase()) {
    case 'easy': return $t('page.jobs.careerGraph.difficultyEasy') as string;
    case 'medium': return $t('page.jobs.careerGraph.difficultyMedium') as string;
    case 'hard': return $t('page.jobs.careerGraph.difficultyHard') as string;
    default: return difficulty || '-';
  }
}

function getTransitionDifficultyType(difficulty?: string): 'success' | 'warning' | 'error' | 'default' {
  switch ((difficulty || '').toLowerCase()) {
    case 'easy': return 'success';
    case 'medium': return 'warning';
    case 'hard': return 'error';
    default: return 'default';
  }
}

async function handleGenerateProfile() {
  if (!jobId.value) return;

  generatingProfile.value = true;
  window.$message?.info($t('page.jobs.profileGenerating') as string, { duration: 5000 });
  try {
    const { data, error } = await fetchGenerateJobCapabilityProfile(jobId.value);
    if (!error && data) {
      capabilityProfile.value = data;
      window.$message?.success($t('page.jobs.profileGenerated') as string);
    } else {
      window.$message?.error(
        extractApiError(error, $t('page.jobs.createFailed') as string),
        { duration: 6000 }
      );
    }
  } catch (err) {
    console.error($t('page.jobs.generateProfileError'), err);
    window.$message?.error(
      extractApiError(err, $t('page.jobs.createFailed') + $t('page.jobs.retryLater')),
      { duration: 6000 }
    );
  } finally {
    generatingProfile.value = false;
  }
}

async function handleGenerateMatch() {
  if (!jobId.value) return;
  generatingMatch.value = true;
  window.$message?.info($t('page.jobs.matchAnalyzing') as string, { duration: 5000 });
  try {
    const { data, error } = await fetchGenerateJobStudentMatch(jobId.value);
    if (!error && data) {
      matchResult.value = data;
      window.$message?.success($t('page.jobs.matchSuccess') as string);
    } else {
      window.$message?.error(
        extractApiError(error, $t('page.jobs.matchFailed') as string),
        { duration: 6000 }
      );
    }
  } catch (err) {
    window.$message?.error(
      extractApiError(err, $t('page.jobs.matchFailedRetry') as string),
      { duration: 6000 }
    );
  } finally {
    generatingMatch.value = false;
  }
}

// --- 职业发展报告：生成 / 润色 / 完整性检查 ---
function openCareerReportDrawer() {
  careerReportDrawerVisible.value = true;
  // 抽屉打开时刷新一次，避免他端更新
  loadCareerReport();
}

/**
 * 判断是否是网关层 504 超时（Nginx 等掐断了 axios，与业务无关）。
 * axios 此时 response.status === 504，response.data 通常是 Nginx HTML，不是后端 JSON。
 */
function isGatewayTimeout(errLike: any): boolean {
  const status = errLike?.response?.status ?? errLike?.status;
  if (status === 504 || status === 502) return true;
  // axios 自身超时
  if (errLike?.code === 'ECONNABORTED' || /timeout/i.test(errLike?.message || '')) return true;
  return false;
}

/**
 * 网关 504 时的兜底：后端可能还在跑，跑完会把记录写进数据库，
 * 这里轮询查最新报告，命中即认为成功。
 * @param totalMs 总轮询时长（默认 90 秒）
 * @param intervalMs 间隔（默认 5 秒）
 * @param baseline 之前已有的报告（用 version/id 做新旧对比，避免拿到旧数据当成新生成）
 */
async function pollLatestCareerReport(
  totalMs = 90_000,
  intervalMs = 5_000,
  baseline?: CareerReportApi.CareerReport | null
): Promise<CareerReportApi.CareerReport | null> {
  if (!jobId.value) return null;
  const start = Date.now();
  // 用 id + version 联合判断"是否是新一份报告"
  const baselineKey = baseline ? `${baseline.id ?? ''}-${baseline.version ?? ''}` : '';
  while (Date.now() - start < totalMs) {
    await new Promise(r => setTimeout(r, intervalMs));
    try {
      const { data, error } = await fetchGetLatestCareerReport(jobId.value);
      if (!error && data) {
        const currentKey = `${data.id ?? ''}-${data.version ?? ''}`;
        if (currentKey && currentKey !== baselineKey) {
          return data;
        }
      }
    } catch {
      // 单次失败忽略，继续轮询
    }
  }
  return null;
}

async function handleGenerateCareerReport() {
  if (!jobId.value) return;
  generatingCareerReport.value = true;
  // 记录基线，便于 504 后判断是否产出了新报告
  const baseline = careerReport.value;
  window.$message?.info($t('page.jobs.careerReport.generating') as string, { duration: 6000 });
  try {
    const payload: CareerReportApi.CareerReportGenerateReq = {
      userPreference: careerReportGenerateForm.value.userPreference?.trim() || undefined,
      focus: careerReportGenerateForm.value.focus?.trim() || undefined
    };
    const { data, error } = await fetchGenerateCareerReport(jobId.value, payload);
    if (!error && data) {
      careerReport.value = data;
      careerReportDrawerVisible.value = true;
      window.$message?.success($t('page.jobs.careerReport.generateSuccess') as string);
      return;
    }
    // 后端报错：504 网关超时走兜底轮询；其他直接展示
    if (isGatewayTimeout(error)) {
      window.$message?.warning($t('page.jobs.careerReport.gatewayTimeoutFallback') as string, { duration: 6000 });
      const polled = await pollLatestCareerReport(120_000, 5_000, baseline);
      if (polled) {
        careerReport.value = polled;
        careerReportDrawerVisible.value = true;
        window.$message?.success($t('page.jobs.careerReport.generateSuccess') as string);
      } else {
        window.$message?.error($t('page.jobs.careerReport.gatewayTimeoutHint') as string, { duration: 8000 });
      }
    } else {
      window.$message?.error(
        extractApiError(error, $t('page.jobs.careerReport.generateFailed') as string),
        { duration: 6000 }
      );
    }
  } catch (err) {
    if (isGatewayTimeout(err)) {
      window.$message?.warning($t('page.jobs.careerReport.gatewayTimeoutFallback') as string, { duration: 6000 });
      const polled = await pollLatestCareerReport(120_000, 5_000, baseline);
      if (polled) {
        careerReport.value = polled;
        careerReportDrawerVisible.value = true;
        window.$message?.success($t('page.jobs.careerReport.generateSuccess') as string);
      } else {
        window.$message?.error($t('page.jobs.careerReport.gatewayTimeoutHint') as string, { duration: 8000 });
      }
    } else {
      window.$message?.error(
        extractApiError(err, $t('page.jobs.careerReport.generateFailed') as string),
        { duration: 6000 }
      );
    }
  } finally {
    generatingCareerReport.value = false;
  }
}

async function handlePolishCareerReport() {
  if (!careerReport.value?.id) return;
  polishingCareerReport.value = true;
  const baseline = careerReport.value;
  window.$message?.info($t('page.jobs.careerReport.polishing') as string, { duration: 5000 });

  const handleFailure = async (errLike: any, fallback: string) => {
    if (isGatewayTimeout(errLike)) {
      window.$message?.warning($t('page.jobs.careerReport.gatewayTimeoutFallback') as string, { duration: 6000 });
      const polled = await pollLatestCareerReport(120_000, 5_000, baseline);
      if (polled) {
        careerReport.value = polled;
        careerReportPolishInstruction.value = '';
        window.$message?.success($t('page.jobs.careerReport.polishSuccess') as string);
      } else {
        window.$message?.error($t('page.jobs.careerReport.gatewayTimeoutHint') as string, { duration: 8000 });
      }
    } else {
      window.$message?.error(extractApiError(errLike, fallback), { duration: 6000 });
    }
  };

  try {
    const { data, error } = await fetchPolishCareerReport(careerReport.value.id, {
      instruction: careerReportPolishInstruction.value?.trim() || undefined
    });
    if (!error && data) {
      careerReport.value = data;
      careerReportPolishInstruction.value = '';
      window.$message?.success($t('page.jobs.careerReport.polishSuccess') as string);
    } else {
      await handleFailure(error, $t('page.jobs.careerReport.polishFailed') as string);
    }
  } catch (err) {
    await handleFailure(err, $t('page.jobs.careerReport.polishFailed') as string);
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
    if (!error && data) {
      careerReportCheckResult.value = data;
      // 检查后报告记录的 status / completenessScore 会被后端写回，重新拉一次保持一致
      await loadCareerReport();
      window.$message?.success($t('page.jobs.careerReport.checkSuccess') as string);
    } else {
      window.$message?.error(
        extractApiError(error, $t('page.jobs.careerReport.checkFailed') as string),
        { duration: 6000 }
      );
    }
  } catch (err) {
    window.$message?.error(
      extractApiError(err, $t('page.jobs.careerReport.checkFailed') as string),
      { duration: 6000 }
    );
  } finally {
    checkingCareerReport.value = false;
  }
}

function getCareerReportStatusLabel(status?: number): string {
  switch (status) {
    case 1: return $t('page.jobs.careerReport.status.draft') as string;
    case 2: return $t('page.jobs.careerReport.status.checked') as string;
    case 3: return $t('page.jobs.careerReport.status.published') as string;
    case 4: return $t('page.jobs.careerReport.status.checkFailed') as string;
    default: return $t('page.jobs.careerReport.status.unknown') as string;
  }
}

function getCareerReportStatusType(status?: number): 'default' | 'info' | 'success' | 'warning' | 'error' {
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
  return (Object.keys(labels) as Array<keyof typeof labels>)
    .map(key => ({
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
  if (value == null) return '-';
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

const evidenceData = computed(() => {
  const raw = careerReport.value?.reportContent?.evidence;
  if (!raw || typeof raw !== 'object') return null;
  return {
    industry: Array.isArray(raw.industryEvidence) ? (raw.industryEvidence as string[]) : [],
    technical: Array.isArray(raw.technicalEvidence) ? (raw.technicalEvidence as string[]) : [],
    communication: Array.isArray(raw.communicationEvidence) ? (raw.communicationEvidence as string[]) : [],
  };
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
      verification: t?.verification || '',
    }));
  };
  return {
    shortTerm: mapTasks(raw.shortTerm),
    midTerm: mapTasks(raw.midTerm),
  };
});

const careerPathData = computed(() => {
  const raw = careerReport.value?.reportContent?.careerPath;
  if (!raw || typeof raw !== 'object') return null;
  return {
    nextStep: (raw.nextStep as string) || '',
    entryPoint: (raw.entryPoint as string) || '',
    transitionLogic: (raw.transitionLogic as string) || '',
    alternativePaths: Array.isArray(raw.alternativePaths) ? (raw.alternativePaths as string[]) : [],
  };
});

const careerGoalsData = computed(() => {
  const raw = careerReport.value?.reportContent?.careerGoals;
  if (!raw || typeof raw !== 'object') return null;
  return {
    shortTerm: (raw.shortTerm as string) || '',
    midTerm: (raw.midTerm as string) || '',
    longTerm: (raw.longTerm as string) || '',
  };
});

const evaluationPlanData = computed(() => {
  const raw = careerReport.value?.reportContent?.evaluationPlan;
  if (!raw || typeof raw !== 'object') return null;
  const metrics: EvaluationMetric[] = Array.isArray(raw.quantitativeMetrics)
    ? raw.quantitativeMetrics.map((m: any) => ({
        metric: m?.metric || '',
        target: m?.target || '',
        deadline: m?.deadline || '',
      }))
    : [];
  const cycles: string[] = Array.isArray(raw.evaluationCycle) ? raw.evaluationCycle : [];
  return { cycles, metrics };
});

const careerExplorationData = computed(() => {
  const raw = careerReport.value?.reportContent?.careerExploration;
  if (!raw || typeof raw !== 'object') return null;
  return {
    roleClarity: (raw.roleClarity as string) || '',
    industryInsight: (raw.industryInsight as string) || '',
    marketPositioning: (raw.marketPositioning as string) || '',
  };
});

const KNOWN_SECTION_KEYS = new Set([
  'evidence', 'actionPlan', 'careerPath', 'careerGoals',
  'evaluationPlan', 'careerExploration',
]);

const unknownSections = computed(() => {
  return reportSections.value.filter(s => !KNOWN_SECTION_KEYS.has(s.key));
});

const sortedVerticalPath = computed(() => {
  const path = careerGraph.value?.verticalPath;
  if (!path?.length) return [];
  return [...path].sort((a, b) => (a.level ?? 0) - (b.level ?? 0));
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
    <div class="h-full p-6 bg-slate-50/50 dark:bg-dark-100 min-h-[500px]">
      <!-- 头部操作栏 -->
      <div class="mb-6 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <NButton quaternary circle @click="handleBack">
            <template #icon><span>←</span></template>
          </NButton>
          <h1 class="text-2xl font-bold text-slate-800 dark:text-gray-200">{{ $t('page.jobs.viewDetail') }}</h1>
        </div>
        <div class="flex gap-3">
          <NButton @click="handleGenerateMatch" :loading="generatingMatch">
            <template #icon><span>🎯</span></template>
            {{ matchResult ? $t('page.jobs.reanalyzeMatch') : $t('page.jobs.analyzeMatch') }}
          </NButton>
          <NButton @click="handleGenerateProfile" :loading="generatingProfile">
            <template #icon><span>🧠</span></template>
            {{ capabilityProfile ? $t('page.jobs.regenerateProfile') : $t('page.jobs.generateProfile') }}
          </NButton>
          <NButton
            type="info"
            ghost
            @click="openCareerGraphDrawer"
            :loading="loadingCareerGraph"
          >
            <template #icon><span>🗺️</span></template>
            {{ careerGraph ? $t('page.jobs.careerGraph.view') : $t('page.jobs.careerGraph.entry') }}
          </NButton>
          <NButton
            type="info"
            ghost
            @click="openCareerReportDrawer"
            :loading="loadingCareerReport"
          >
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

      <div v-if="jobDetail" class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- 左侧：岗位信息 -->
        <div class="lg:col-span-2 space-y-6">
          <!-- 基本信息 -->
          <NCard :title="$t('page.jobs.basicInfo')" class="rounded-xl">
            <div class="space-y-4">
              <div class="flex items-center justify-between py-2 border-b border-slate-100 dark:border-gray-700">
                <span class="text-slate-600 dark:text-gray-400">{{ $t('page.jobs.jobName') }}</span>
                <span class="font-semibold text-slate-800 dark:text-gray-200">{{ jobDetail.jobName }}</span>
              </div>
              <div class="flex items-center justify-between py-2 border-b border-slate-100 dark:border-gray-700">
                <span class="text-slate-600 dark:text-gray-400">{{ $t('page.jobs.companyName') }}</span>
                <span class="font-semibold text-slate-800 dark:text-gray-200">{{ jobDetail.companyName }}</span>
              </div>
              <div class="flex items-center justify-between py-2 border-b border-slate-100 dark:border-gray-700">
                <span class="text-slate-600 dark:text-gray-400">{{ $t('page.jobs.location') }}</span>
                <span class="font-semibold text-slate-800 dark:text-gray-200">{{ jobDetail.location }}</span>
              </div>
              <div class="flex items-center justify-between py-2">
                <span class="text-slate-600 dark:text-gray-400">{{ $t('page.jobs.salaryInfo') }}</span>
                <span class="font-semibold text-orange-600">{{ formatSalary(jobDetail) }}</span>
              </div>
            </div>
          </NCard>

          <!-- 岗位描述 -->
          <NCard :title="$t('page.jobs.jobDescription')" class="rounded-xl">
            <p class="text-slate-700 dark:text-gray-300 leading-relaxed whitespace-pre-wrap">{{ jobDetail.description }}</p>
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
              <li v-for="(duty, idx) in jobDetail.jobDuties" :key="idx" class="text-slate-700 dark:text-gray-300 flex items-start gap-2">
                <span class="text-blue-500 mt-1">•</span>
                <span>{{ duty }}</span>
              </li>
            </ul>
          </NCard>

          <!-- 岗位要求 -->
          <NCard v-if="jobDetail.jobRequirements && jobDetail.jobRequirements.length > 0" :title="$t('page.jobs.jobRequirements')" class="rounded-xl">
            <ul class="space-y-2">
              <li v-for="(req, idx) in jobDetail.jobRequirements" :key="idx" class="text-slate-700 dark:text-gray-300 flex items-start gap-2">
                <span class="text-blue-500 mt-1">•</span>
                <span>{{ req }}</span>
              </li>
            </ul>
          </NCard>

          <!-- 公司信息 -->
          <NCard v-if="jobDetail.companyIndustries" :title="$t('page.jobs.companyIndustry') + $t('page.jobs.infoSuffix')" class="rounded-xl">
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
                <template #icon><span>🔄</span></template>
              </NButton>
            </template>

            <div v-if="capabilityProfile" class="space-y-4">
              <!-- 岗位类型 -->
              <div v-if="capabilityProfile.targetRoleType">
                <h4 class="font-semibold text-slate-800 dark:text-gray-200 mb-2 flex items-center gap-2">
                  <span class="text-blue-500">💼</span>
                  {{ $t('page.jobs.jobName') + $t('page.jobs.jobType') }}
                </h4>
                <p class="text-slate-700 dark:text-gray-300">{{ capabilityProfile.targetRoleType }}</p>
              </div>

              <!-- 专业技能 -->
              <div v-if="capabilityProfile.professionalSkills">
                <h4 class="font-semibold text-slate-800 dark:text-gray-200 mb-2 flex items-center gap-2">
                  <span class="text-blue-500">💻</span>
                  {{ $t('page.jobs.technicalSkills') }}
                </h4>
                <p class="text-slate-700 dark:text-gray-300 whitespace-pre-wrap">{{ capabilityProfile.professionalSkills }}</p>
              </div>

              <!-- 证书 -->
              <div v-if="capabilityProfile.certificates">
                <h4 class="font-semibold text-slate-800 dark:text-gray-200 mb-2 flex items-center gap-2">
                  <span class="text-purple-500">🎓</span>
                  {{ $t('page.jobs.certificateRequired') }}
                </h4>
                <p class="text-slate-700 dark:text-gray-300 whitespace-pre-wrap">{{ capabilityProfile.certificates }}</p>
              </div>

              <!-- 七维能力 -->
              <div class="grid grid-cols-1 gap-3">
                <div v-if="capabilityProfile.innovationAbility" class="bg-blue-50 dark:bg-blue-900/20 p-3 rounded-lg">
                  <div class="font-medium text-slate-800 dark:text-gray-200 mb-1 flex items-center gap-2">
                    <span class="text-blue-500">💡</span>
                    {{ $t('page.jobs.innovationAbility') }}
                  </div>
                  <p class="text-sm text-slate-700 dark:text-gray-300">{{ capabilityProfile.innovationAbility }}</p>
                </div>

                <div v-if="capabilityProfile.learningAbility" class="bg-green-50 dark:bg-green-900/20 p-3 rounded-lg">
                  <div class="font-medium text-slate-800 dark:text-gray-200 mb-1 flex items-center gap-2">
                    <span class="text-green-500">🎓</span>
                    {{ $t('page.jobs.learningAbility') }}
                  </div>
                  <p class="text-sm text-slate-700 dark:text-gray-300">{{ capabilityProfile.learningAbility }}</p>
                </div>

                <div v-if="capabilityProfile.pressureResistance" class="bg-orange-50 dark:bg-orange-900/20 p-3 rounded-lg">
                  <div class="font-medium text-slate-800 dark:text-gray-200 mb-1 flex items-center gap-2">
                    <span class="text-orange-500">🛡️</span>
                    {{ $t('page.jobs.pressureResistance') }}
                  </div>
                  <p class="text-sm text-slate-700 dark:text-gray-300">{{ capabilityProfile.pressureResistance }}</p>
                </div>

                <div v-if="capabilityProfile.communicationAbility" class="bg-cyan-50 dark:bg-cyan-900/20 p-3 rounded-lg">
                  <div class="font-medium text-slate-800 dark:text-gray-200 mb-1 flex items-center gap-2">
                    <span class="text-cyan-500">🗣️</span>
                    {{ $t('page.jobs.communicationAbility') }}
                  </div>
                  <p class="text-sm text-slate-700 dark:text-gray-300">{{ capabilityProfile.communicationAbility }}</p>
                </div>

                <div v-if="capabilityProfile.practicalAbility" class="bg-indigo-50 dark:bg-indigo-900/20 p-3 rounded-lg">
                  <div class="font-medium text-slate-800 dark:text-gray-200 mb-1 flex items-center gap-2">
                    <span class="text-indigo-500">🔨</span>
                    {{ $t('page.jobs.practicalAbility') }}
                  </div>
                  <p class="text-sm text-slate-700 dark:text-gray-300">{{ capabilityProfile.practicalAbility }}</p>
                </div>
              </div>

              <!-- 岗位优势 -->
              <div v-if="capabilityProfile.strengths && capabilityProfile.strengths.length > 0">
                <h4 class="font-semibold text-slate-800 dark:text-gray-200 mb-2 flex items-center gap-2">
                  <span class="text-yellow-500">⭐</span>
                  {{ $t('page.jobs.strengths') }}
                </h4>
                <ul class="space-y-1">
                  <li v-for="(strength, idx) in capabilityProfile.strengths" :key="idx" class="text-sm text-slate-700 dark:text-gray-300 flex items-start gap-2">
                    <span class="text-green-500">✓</span>
                    <span>{{ strength }}</span>
                  </li>
                </ul>
              </div>

              <!-- 缺失技能 -->
              <div v-if="capabilityProfile.missingSkills && capabilityProfile.missingSkills.length > 0">
                <h4 class="font-semibold text-slate-800 dark:text-gray-200 mb-2 flex items-center gap-2">
                  <span class="text-red-500">⚠️</span>
                  {{ $t('page.jobs.missingSkills') }}
                </h4>
                <ul class="space-y-1">
                  <li v-for="(skill, idx) in capabilityProfile.missingSkills" :key="idx" class="text-sm text-slate-700 dark:text-gray-300 flex items-start gap-2">
                    <span class="text-red-500">✗</span>
                    <span>{{ skill }}</span>
                  </li>
                </ul>
              </div>

              <!-- 证据不足项 -->
              <div v-if="capabilityProfile.weakEvidenceItems && capabilityProfile.weakEvidenceItems.length > 0">
                <h4 class="font-semibold text-slate-800 dark:text-gray-200 mb-2 flex items-center gap-2">
                  <span class="text-orange-500">❓</span>
                  {{ $t('page.jobs.weakEvidenceItems') }}
                </h4>
                <ul class="space-y-1">
                  <li v-for="(item, idx) in capabilityProfile.weakEvidenceItems" :key="idx" class="text-sm text-slate-700 dark:text-gray-300 flex items-start gap-2">
                    <span class="text-orange-500">!</span>
                    <span>{{ item }}</span>
                  </li>
                </ul>
              </div>

              <!-- 总结 -->
              <div v-if="capabilityProfile.summary" class="bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-900/20 dark:to-indigo-900/20 p-4 rounded-lg border border-blue-200 dark:border-blue-800">
                <h4 class="font-semibold text-slate-800 dark:text-gray-200 mb-2 flex items-center gap-2">
                  <span class="text-blue-600">📝</span>
                  {{ $t('page.jobs.summary') }}
                </h4>
                <p class="text-slate-700 dark:text-gray-300 italic">{{ capabilityProfile.summary }}</p>
              </div>
            </div>

            <NEmpty v-else :description="$t('page.jobs.noProfile')" class="py-8">
              <template #extra>
                <NButton type="primary" :loading="generatingProfile" @click="handleGenerateProfile">
                  <template #icon><span>🧠</span></template>
                  {{ $t('page.jobs.generateProfile') }}
                </NButton>
              </template>
            </NEmpty>
          </NCard>
        </div>
      </div>
    </div>

    <!-- 职业发展报告 抽屉 -->
    <NDrawer
      v-model:show="careerReportDrawerVisible"
      :width="720"
      :auto-focus="false"
      placement="right"
    >
      <NDrawerContent
        :title="$t('page.jobs.careerReport.drawerTitle')"
        closable
      >
        <!-- 顶部：状态条 + 操作 -->
        <div class="mb-4 flex items-center justify-between flex-wrap gap-3">
          <div class="flex items-center gap-2 flex-wrap">
            <template v-if="careerReport">
              <NTag :type="getCareerReportStatusType(careerReport.status)" round size="small">
                {{ $t('page.jobs.careerReport.statusLabel') }}：{{ getCareerReportStatusLabel(careerReport.status) }}
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
            <NButton
              size="small"
              type="primary"
              :loading="generatingCareerReport"
              @click="handleGenerateCareerReport"
            >
              <template #icon><span>{{ careerReport ? '🔄' : '✨' }}</span></template>
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
          class="mb-4 bg-blue-50/40 dark:bg-blue-900/10 rounded-lg"
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
          <div class="space-y-2 text-sm">
            <div class="flex items-center gap-2 flex-wrap">
              <NTag
                :type="careerReportCheckResult.passed ? 'success' : 'error'"
                size="small"
              >
                {{ careerReportCheckResult.passed ? '✅ 通过' : '❌ 未通过' }}
              </NTag>
              <NTag v-if="careerReportCheckResult.completenessScore != null" type="warning" size="small">
                {{ $t('page.jobs.careerReport.completenessScore') }}：{{ careerReportCheckResult.completenessScore }}/100
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
          <NCard
            v-if="evidenceData"
            size="small"
            title="📋 证据支撑"
            class="mb-4 rounded-lg"
          >
            <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div class="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-3 border border-blue-100 dark:border-blue-800/40">
                <div class="flex items-center gap-1.5 mb-2 text-blue-700 dark:text-blue-400 font-semibold text-sm">
                  <span>🏭</span> 行业证据
                </div>
                <ul class="space-y-1.5">
                  <li v-for="(item, i) in evidenceData.industry" :key="'ind-'+i" class="text-xs text-slate-700 dark:text-gray-300 flex items-start gap-1.5">
                    <span class="text-blue-400 mt-0.5 shrink-0">◆</span>
                    <span>{{ item }}</span>
                  </li>
                </ul>
              </div>
              <div class="bg-emerald-50 dark:bg-emerald-900/20 rounded-lg p-3 border border-emerald-100 dark:border-emerald-800/40">
                <div class="flex items-center gap-1.5 mb-2 text-emerald-700 dark:text-emerald-400 font-semibold text-sm">
                  <span>💻</span> 技术证据
                </div>
                <ul class="space-y-1.5">
                  <li v-for="(item, i) in evidenceData.technical" :key="'tec-'+i" class="text-xs text-slate-700 dark:text-gray-300 flex items-start gap-1.5">
                    <span class="text-emerald-400 mt-0.5 shrink-0">◆</span>
                    <span>{{ item }}</span>
                  </li>
                </ul>
              </div>
              <div class="bg-violet-50 dark:bg-violet-900/20 rounded-lg p-3 border border-violet-100 dark:border-violet-800/40">
                <div class="flex items-center gap-1.5 mb-2 text-violet-700 dark:text-violet-400 font-semibold text-sm">
                  <span>🗣️</span> 沟通证据
                </div>
                <ul class="space-y-1.5">
                  <li v-for="(item, i) in evidenceData.communication" :key="'com-'+i" class="text-xs text-slate-700 dark:text-gray-300 flex items-start gap-1.5">
                    <span class="text-violet-400 mt-0.5 shrink-0">◆</span>
                    <span>{{ item }}</span>
                  </li>
                </ul>
              </div>
            </div>
          </NCard>

          <!-- actionPlan：短期 / 中期行动计划 -->
          <NCard
            v-if="actionPlanData"
            size="small"
            title="📅 行动计划"
            class="mb-4 rounded-lg"
          >
            <!-- 短期计划 -->
            <div v-if="actionPlanData.shortTerm.length" class="mb-4">
              <div class="flex items-center gap-2 mb-3">
                <span class="text-orange-500 text-base">🎯</span>
                <span class="font-semibold text-slate-800 dark:text-gray-200">短期计划</span>
                <NTag size="tiny" type="warning" round>近期执行</NTag>
              </div>
              <div class="space-y-3">
                <div
                  v-for="(t, i) in actionPlanData.shortTerm"
                  :key="'st-'+i"
                  class="bg-orange-50/60 dark:bg-orange-900/10 rounded-lg p-3 border border-orange-100 dark:border-orange-800/30"
                >
                  <div class="flex items-start gap-2 mb-2">
                    <span class="bg-orange-500 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center shrink-0 mt-0.5">{{ i + 1 }}</span>
                    <span class="text-sm font-medium text-slate-800 dark:text-gray-200">{{ t.task }}</span>
                  </div>
                  <div class="grid grid-cols-1 sm:grid-cols-3 gap-2 ml-7">
                    <div class="flex items-center gap-1 text-xs text-slate-600 dark:text-gray-400">
                      <span class="text-orange-400">⏱️</span>
                      <span class="font-medium">周期：</span>{{ t.cycle }}
                    </div>
                    <div class="flex items-center gap-1 text-xs text-slate-600 dark:text-gray-400">
                      <span class="text-orange-400">📦</span>
                      <span class="font-medium">交付物：</span>{{ t.deliverable }}
                    </div>
                    <div class="flex items-center gap-1 text-xs text-slate-600 dark:text-gray-400">
                      <span class="text-orange-400">✅</span>
                      <span class="font-medium">验证：</span>{{ t.verification }}
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 中期计划 -->
            <div v-if="actionPlanData.midTerm.length">
              <div class="flex items-center gap-2 mb-3">
                <span class="text-blue-500 text-base">🚀</span>
                <span class="font-semibold text-slate-800 dark:text-gray-200">中期计划</span>
                <NTag size="tiny" type="info" round>持续成长</NTag>
              </div>
              <div class="space-y-3">
                <div
                  v-for="(t, i) in actionPlanData.midTerm"
                  :key="'mt-'+i"
                  class="bg-blue-50/60 dark:bg-blue-900/10 rounded-lg p-3 border border-blue-100 dark:border-blue-800/30"
                >
                  <div class="flex items-start gap-2 mb-2">
                    <span class="bg-blue-500 text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center shrink-0 mt-0.5">{{ i + 1 }}</span>
                    <span class="text-sm font-medium text-slate-800 dark:text-gray-200">{{ t.task }}</span>
                  </div>
                  <div class="grid grid-cols-1 sm:grid-cols-3 gap-2 ml-7">
                    <div class="flex items-center gap-1 text-xs text-slate-600 dark:text-gray-400">
                      <span class="text-blue-400">⏱️</span>
                      <span class="font-medium">周期：</span>{{ t.cycle }}
                    </div>
                    <div class="flex items-center gap-1 text-xs text-slate-600 dark:text-gray-400">
                      <span class="text-blue-400">📦</span>
                      <span class="font-medium">交付物：</span>{{ t.deliverable }}
                    </div>
                    <div class="flex items-center gap-1 text-xs text-slate-600 dark:text-gray-400">
                      <span class="text-blue-400">✅</span>
                      <span class="font-medium">验证：</span>{{ t.verification }}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </NCard>

          <!-- careerPath：职业路径 -->
          <NCard
            v-if="careerPathData"
            size="small"
            title="🗺️ 职业路径"
            class="mb-4 rounded-lg"
          >
            <!-- 入口 → 下一步 -->
            <div class="flex flex-col sm:flex-row items-center gap-2 sm:gap-4 mb-4">
              <div class="bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-800/40 rounded-lg px-4 py-2.5 text-center min-w-[140px]">
                <div class="text-xs text-indigo-500 dark:text-indigo-400 font-medium mb-0.5">🏭 入职起点</div>
                <div class="text-sm font-semibold text-slate-800 dark:text-gray-200">{{ careerPathData.entryPoint }}</div>
              </div>
              <div class="text-indigo-400 text-xl transform sm:rotate-0 rotate-90">→</div>
              <div class="bg-indigo-50 dark:bg-indigo-900/20 border border-indigo-200 dark:border-indigo-800/40 rounded-lg px-4 py-2.5 text-center min-w-[140px]">
                <div class="text-xs text-indigo-500 dark:text-indigo-400 font-medium mb-0.5">🚀 下一步发展</div>
                <div class="text-sm font-semibold text-slate-800 dark:text-gray-200">{{ careerPathData.nextStep }}</div>
              </div>
            </div>

            <!-- 过渡逻辑 -->
            <div class="bg-slate-50 dark:bg-gray-800/40 rounded-lg p-3 mb-3">
              <div class="flex items-center gap-1.5 mb-1.5 text-slate-600 dark:text-gray-400 text-xs font-semibold">
                <span>💡</span> 过渡逻辑
              </div>
              <p class="text-sm text-slate-700 dark:text-gray-300 leading-relaxed">{{ careerPathData.transitionLogic }}</p>
            </div>

            <!-- 备选路径 -->
            <div v-if="careerPathData.alternativePaths.length">
              <div class="flex items-center gap-1.5 mb-2 text-slate-600 dark:text-gray-400 text-xs font-semibold">
                <span>🔄</span> 备选路径
              </div>
              <div class="flex flex-wrap gap-2">
                <NTag
                  v-for="(alt, i) in careerPathData.alternativePaths"
                  :key="'alt-'+i"
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
          <NCard
            v-if="careerGoalsData"
            size="small"
            title="🎯 职业目标"
            class="mb-4 rounded-lg"
          >
            <div class="relative">
              <!-- 时间线竖线 -->
              <div class="absolute left-[11px] top-2 bottom-2 w-0.5 bg-gradient-to-b from-amber-400 via-rose-400 to-violet-500"></div>
              <div class="space-y-4">
                <!-- 短期 -->
                <div v-if="careerGoalsData.shortTerm" class="flex items-start gap-3 relative">
                  <span class="bg-amber-500 text-white text-xs font-bold rounded-full w-6 h-6 flex items-center justify-center shrink-0 relative z-10 shadow">1</span>
                  <div class="bg-amber-50 dark:bg-amber-900/10 border border-amber-100 dark:border-amber-800/30 rounded-lg p-3 flex-1">
                    <div class="flex items-center gap-2 mb-1">
                      <span class="font-semibold text-sm text-slate-800 dark:text-gray-200">短期目标</span>
                      <NTag size="tiny" type="warning" round>6个月内</NTag>
                    </div>
                    <p class="text-sm text-slate-700 dark:text-gray-300 leading-relaxed">{{ careerGoalsData.shortTerm }}</p>
                  </div>
                </div>
                <!-- 中期 -->
                <div v-if="careerGoalsData.midTerm" class="flex items-start gap-3 relative">
                  <span class="bg-rose-500 text-white text-xs font-bold rounded-full w-6 h-6 flex items-center justify-center shrink-0 relative z-10 shadow">2</span>
                  <div class="bg-rose-50 dark:bg-rose-900/10 border border-rose-100 dark:border-rose-800/30 rounded-lg p-3 flex-1">
                    <div class="flex items-center gap-2 mb-1">
                      <span class="font-semibold text-sm text-slate-800 dark:text-gray-200">中期目标</span>
                      <NTag size="tiny" type="error" round>2年内</NTag>
                    </div>
                    <p class="text-sm text-slate-700 dark:text-gray-300 leading-relaxed">{{ careerGoalsData.midTerm }}</p>
                  </div>
                </div>
                <!-- 长期 -->
                <div v-if="careerGoalsData.longTerm" class="flex items-start gap-3 relative">
                  <span class="bg-violet-500 text-white text-xs font-bold rounded-full w-6 h-6 flex items-center justify-center shrink-0 relative z-10 shadow">3</span>
                  <div class="bg-violet-50 dark:bg-violet-900/10 border border-violet-100 dark:border-violet-800/30 rounded-lg p-3 flex-1">
                    <div class="flex items-center gap-2 mb-1">
                      <span class="font-semibold text-sm text-slate-800 dark:text-gray-200">长期目标</span>
                      <NTag size="tiny" type="info" round>5年内</NTag>
                    </div>
                    <p class="text-sm text-slate-700 dark:text-gray-300 leading-relaxed">{{ careerGoalsData.longTerm }}</p>
                  </div>
                </div>
              </div>
            </div>
          </NCard>

          <!-- evaluationPlan：评估计划 -->
          <NCard
            v-if="evaluationPlanData"
            size="small"
            title="📊 评估计划"
            class="mb-4 rounded-lg"
          >
            <!-- 评估周期 -->
            <div v-if="evaluationPlanData.cycles.length" class="mb-4">
              <div class="text-xs font-semibold text-slate-600 dark:text-gray-400 mb-2 flex items-center gap-1.5">
                <span>📅</span> 评估周期
              </div>
              <div class="flex flex-wrap gap-2">
                <NTag
                  v-for="(c, i) in evaluationPlanData.cycles"
                  :key="'cyc-'+i"
                  type="success"
                  round
                  size="small"
                >
                  {{ c }}
                </NTag>
              </div>
            </div>

            <!-- 量化指标表 -->
            <div v-if="evaluationPlanData.metrics.length">
              <div class="text-xs font-semibold text-slate-600 dark:text-gray-400 mb-2 flex items-center gap-1.5">
                <span>📈</span> 量化指标
              </div>
              <div class="overflow-x-auto">
                <table class="w-full text-xs border-collapse">
                  <thead>
                    <tr class="bg-slate-100 dark:bg-gray-800/60">
                      <th class="text-left p-2.5 rounded-l-lg font-semibold text-slate-700 dark:text-gray-300 w-[50%]">指标</th>
                      <th class="text-center p-2.5 font-semibold text-slate-700 dark:text-gray-300 w-[20%]">目标值</th>
                      <th class="text-right p-2.5 rounded-r-lg font-semibold text-slate-700 dark:text-gray-300 w-[30%]">截止时间</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="(m, i) in evaluationPlanData.metrics"
                      :key="'met-'+i"
                      class="border-b border-slate-100 dark:border-gray-700/50 last:border-none"
                    >
                      <td class="p-2.5 text-slate-700 dark:text-gray-300">{{ m.metric }}</td>
                      <td class="p-2.5 text-center">
                        <NTag size="tiny" :type="m.target === '100%' || m.target === '达成' ? 'success' : 'warning'" round>
                          {{ m.target }}
                        </NTag>
                      </td>
                      <td class="p-2.5 text-right text-slate-600 dark:text-gray-400">{{ m.deadline }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </NCard>

          <!-- careerExploration：职业探索洞察 -->
          <NCard
            v-if="careerExplorationData"
            size="small"
            title="🔍 职业探索"
            class="mb-4 rounded-lg"
          >
            <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div class="bg-cyan-50 dark:bg-cyan-900/20 rounded-lg p-3 border border-cyan-100 dark:border-cyan-800/40">
                <div class="flex items-center gap-1.5 mb-2 text-cyan-700 dark:text-cyan-400 font-semibold text-sm">
                  <span>💼</span> 角色定位
                </div>
                <p class="text-xs text-slate-700 dark:text-gray-300 leading-relaxed">{{ careerExplorationData.roleClarity }}</p>
              </div>
              <div class="bg-teal-50 dark:bg-teal-900/20 rounded-lg p-3 border border-teal-100 dark:border-teal-800/40">
                <div class="flex items-center gap-1.5 mb-2 text-teal-700 dark:text-teal-400 font-semibold text-sm">
                  <span>🏭</span> 行业洞察
                </div>
                <p class="text-xs text-slate-700 dark:text-gray-300 leading-relaxed">{{ careerExplorationData.industryInsight }}</p>
              </div>
              <div class="bg-purple-50 dark:bg-purple-900/20 rounded-lg p-3 border border-purple-100 dark:border-purple-800/40">
                <div class="flex items-center gap-1.5 mb-2 text-purple-700 dark:text-purple-400 font-semibold text-sm">
                  <span>📈</span> 市场定位
                </div>
                <p class="text-xs text-slate-700 dark:text-gray-300 leading-relaxed">{{ careerExplorationData.marketPositioning }}</p>
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
                <h4 class="font-semibold text-slate-800 dark:text-gray-200 mb-1">{{ sec.key }}</h4>
                <pre class="whitespace-pre-wrap break-words text-sm text-slate-700 dark:text-gray-300 bg-slate-50/60 dark:bg-gray-800/40 p-3 rounded">{{ renderSectionValue(sec.value) }}</pre>
              </div>
            </div>
          </NCard>
        </template>

        <!-- 知识来源 -->
        <NCard
          v-if="careerReport && careerReport.knowledgeSources && careerReport.knowledgeSources.length"
          size="small"
          :title="$t('page.jobs.careerReport.knowledgeSources')"
          class="mb-4 rounded-lg"
        >
          <ul class="space-y-1 text-sm">
            <li
              v-for="(src, i) in careerReport.knowledgeSources"
              :key="'ks-' + i"
              class="text-slate-700 dark:text-gray-300"
            >
              <a v-if="src.url" :href="src.url" target="_blank" class="text-blue-500 hover:underline">{{ src.title || src.url }}</a>
              <span v-else>{{ src.title || '-' }}</span>
              <span v-if="src.snippet" class="ml-2 text-slate-500 dark:text-gray-400">— {{ src.snippet }}</span>
            </li>
          </ul>
        </NCard>

        <!-- 智能润色 -->
        <NCard
          v-if="careerReport"
          size="small"
          :title="$t('page.jobs.careerReport.polish')"
          class="mb-4 rounded-lg"
        >
          <div class="space-y-2">
            <NInput
              v-model:value="careerReportPolishInstruction"
              type="textarea"
              :placeholder="$t('page.jobs.careerReport.polishInstructionPlaceholder') as string"
              :autosize="{ minRows: 2, maxRows: 4 }"
            />
            <div class="flex justify-end">
              <NButton
                size="small"
                type="primary"
                :loading="polishingCareerReport"
                @click="handlePolishCareerReport"
              >
                <template #icon><span>🪄</span></template>
                {{ $t('page.jobs.careerReport.confirmPolish') }}
              </NButton>
            </div>
          </div>
        </NCard>
      </NDrawerContent>
    </NDrawer>

    <!-- 岗位关联图谱 抽屉 -->
    <NDrawer
      v-model:show="careerGraphDrawerVisible"
      :width="720"
      :auto-focus="false"
      placement="right"
    >
      <NDrawerContent
        :title="$t('page.jobs.careerGraph.drawerTitle')"
        closable
      >
        <div class="mb-4 flex items-center justify-between flex-wrap gap-3">
          <div class="flex items-center gap-2 flex-wrap">
            <NTag v-if="careerGraph?.targetRoleType" type="info" round size="small">
              {{ $t('page.jobs.careerGraph.targetRoleType') }}：{{ careerGraph.targetRoleType }}
            </NTag>
          </div>
          <NButton
            size="small"
            type="primary"
            :loading="generatingCareerGraph"
            @click="handleGenerateCareerGraph"
          >
            <template #icon><span>{{ careerGraph ? '🔄' : '✨' }}</span></template>
            {{ careerGraph ? $t('page.jobs.careerGraph.regenerate') : $t('page.jobs.careerGraph.generate') }}
          </NButton>
        </div>

        <template v-if="careerGraph">
          <NCard
            v-if="careerGraph.currentNode"
            size="small"
            :title="$t('page.jobs.careerGraph.currentNode')"
            class="mb-4 rounded-lg"
          >
            <div class="space-y-2">
              <div class="text-base font-semibold text-slate-800 dark:text-gray-200">
                {{ careerGraph.currentNode.title }}
              </div>
              <p v-if="careerGraph.currentNode.description" class="text-sm text-slate-700 dark:text-gray-300 leading-relaxed">
                {{ careerGraph.currentNode.description }}
              </p>
            </div>
          </NCard>

          <NCard
            v-if="sortedVerticalPath.length"
            size="small"
            :title="$t('page.jobs.careerGraph.verticalPath')"
            class="mb-4 rounded-lg"
          >
            <div class="space-y-4">
              <div
                v-for="(node, idx) in sortedVerticalPath"
                :key="'vp-' + idx"
                class="relative pl-6"
              >
                <div
                  class="absolute left-0 top-1.5 w-3 h-3 rounded-full border-2"
                  :class="node.current
                    ? 'bg-indigo-500 border-indigo-500'
                    : 'bg-white dark:bg-gray-800 border-indigo-300 dark:border-indigo-600'"
                />
                <div
                  v-if="idx < sortedVerticalPath.length - 1"
                  class="absolute left-[5px] top-4 bottom-0 w-0.5 bg-indigo-200 dark:bg-indigo-800/60"
                />
                <div
                  class="rounded-lg p-3 border"
                  :class="node.current
                    ? 'bg-indigo-50 dark:bg-indigo-900/20 border-indigo-200 dark:border-indigo-800/40'
                    : 'bg-slate-50 dark:bg-gray-800/40 border-slate-200 dark:border-gray-700'"
                >
                  <div class="flex items-center gap-2 flex-wrap mb-1">
                    <span class="font-semibold text-slate-800 dark:text-gray-200">{{ node.title }}</span>
                    <NTag v-if="node.current" size="tiny" type="info" round>{{ $t('page.jobs.careerGraph.current') }}</NTag>
                    <NTag v-if="node.typicalYears" size="tiny" round>
                      {{ $t('page.jobs.careerGraph.typicalYears') }}：{{ node.typicalYears }}
                    </NTag>
                  </div>
                  <p v-if="node.description" class="text-sm text-slate-700 dark:text-gray-300 mb-2">
                    {{ node.description }}
                  </p>
                  <div v-if="node.responsibilities?.length" class="mb-2">
                    <div class="text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1">
                      {{ $t('page.jobs.careerGraph.responsibilities') }}
                    </div>
                    <ul class="list-disc list-inside text-sm text-slate-700 dark:text-gray-300 space-y-0.5">
                      <li v-for="(item, i) in node.responsibilities" :key="'resp-' + i">{{ item }}</li>
                    </ul>
                  </div>
                  <div v-if="node.keyRequirements?.length">
                    <div class="text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1">
                      {{ $t('page.jobs.careerGraph.keyRequirements') }}
                    </div>
                    <div class="flex flex-wrap gap-1">
                      <NTag v-for="(req, i) in node.keyRequirements" :key="'req-' + i" size="tiny" round>
                        {{ req }}
                      </NTag>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </NCard>

          <NCard
            v-if="careerGraph.transitionPaths?.length"
            size="small"
            :title="$t('page.jobs.careerGraph.transitionPaths')"
            class="mb-4 rounded-lg"
          >
            <div class="space-y-4">
              <div
                v-for="(path, idx) in careerGraph.transitionPaths"
                :key="'tp-' + idx"
                class="rounded-lg p-3 border border-slate-200 dark:border-gray-700 bg-slate-50/60 dark:bg-gray-800/30"
              >
                <div class="flex items-center gap-2 flex-wrap mb-2">
                  <span class="font-semibold text-slate-800 dark:text-gray-200">{{ path.name }}</span>
                  <NTag
                    v-if="path.difficulty"
                    size="tiny"
                    :type="getTransitionDifficultyType(path.difficulty)"
                    round
                  >
                    {{ $t('page.jobs.careerGraph.difficulty') }}：{{ getTransitionDifficultyLabel(path.difficulty) }}
                  </NTag>
                </div>
                <div v-if="path.targetRole" class="text-sm text-slate-700 dark:text-gray-300 mb-2">
                  <span class="font-medium">{{ $t('page.jobs.careerGraph.targetRole') }}：</span>{{ path.targetRole }}
                </div>
                <p v-if="path.reason" class="text-sm text-slate-700 dark:text-gray-300 mb-2">
                  <span class="font-medium">{{ $t('page.jobs.careerGraph.reason') }}：</span>{{ path.reason }}
                </p>
                <div v-if="path.bridgingSkills?.length" class="mb-3">
                  <div class="text-xs font-semibold text-slate-600 dark:text-gray-400 mb-1">
                    {{ $t('page.jobs.careerGraph.bridgingSkills') }}
                  </div>
                  <div class="flex flex-wrap gap-1">
                    <NTag v-for="(skill, i) in path.bridgingSkills" :key="'bs-' + i" size="tiny" type="warning" round>
                      {{ skill }}
                    </NTag>
                  </div>
                </div>
                <div v-if="path.nodes?.length">
                  <div class="text-xs font-semibold text-slate-600 dark:text-gray-400 mb-2">
                    {{ $t('page.jobs.careerGraph.pathNodes') }}
                  </div>
                  <div class="flex flex-col sm:flex-row items-stretch sm:items-center gap-2 flex-wrap">
                    <template v-for="(node, nodeIdx) in path.nodes" :key="'tn-' + nodeIdx">
                      <div class="bg-white dark:bg-gray-800 rounded-lg px-3 py-2 border border-slate-200 dark:border-gray-700 min-w-[120px]">
                        <div class="text-sm font-medium text-slate-800 dark:text-gray-200">{{ node.title }}</div>
                        <p v-if="node.description" class="text-xs text-slate-600 dark:text-gray-400 mt-1">{{ node.description }}</p>
                      </div>
                      <div
                        v-if="nodeIdx < (path.nodes?.length ?? 0) - 1"
                        class="text-slate-400 text-center sm:rotate-0 rotate-90"
                      >
                        →
                      </div>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </NCard>

          <div
            v-if="careerGraph.summary"
            class="bg-gradient-to-r from-indigo-50 to-blue-50 dark:from-indigo-900/20 dark:to-blue-900/20 p-4 rounded-lg border border-indigo-200 dark:border-indigo-800"
          >
            <h4 class="font-semibold text-slate-800 dark:text-gray-200 mb-2 flex items-center gap-2">
              <span class="text-indigo-600">📝</span>
              {{ $t('page.jobs.summary') }}
            </h4>
            <p class="text-slate-700 dark:text-gray-300 italic">{{ careerGraph.summary }}</p>
          </div>
        </template>

        <NEmpty v-else :description="$t('page.jobs.careerGraph.empty')" class="py-12">
          <template #extra>
            <NButton type="primary" :loading="generatingCareerGraph" @click="handleGenerateCareerGraph">
              <template #icon><span>🗺️</span></template>
              {{ $t('page.jobs.careerGraph.generate') }}
            </NButton>
          </template>
        </NEmpty>
      </NDrawerContent>
    </NDrawer>
  </NSpin>
</template>

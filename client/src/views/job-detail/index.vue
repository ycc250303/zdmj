<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { $t } from '@/locales';
import {
  fetchGetJobDetail,
  fetchGetJobCapabilityProfile,
  fetchGenerateJobCapabilityProfile,
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

defineOptions({ name: 'job-detail' });

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

/**
 * 统一从后端错误对象中抽出 code/msg，避免把 LLM 业务错误吞成无意义提示。
 * 兼容两种来源：
 *  1) request 工具返回的 { error } 形态（error 是 AxiosError）
 *  2) try/catch 抛出的 axios 异常
 */
function extractApiError(errLike: any, fallback: string): string {
  const resp = errLike?.response?.data;
  const msg: string = resp?.msg || resp?.message || errLike?.message || fallback;
  const code = resp?.code != null ? `[${resp.code}] ` : '';
  // 命中 LLM 兜底错误 8301 时，给一段更友好的引导
  if (resp?.code === 8301) {
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

        <!-- 报告正文（按章节键平铺）-->
        <NCard
          v-if="careerReport && reportSections.length"
          size="small"
          :title="$t('page.jobs.careerReport.reportContent')"
          class="mb-4 rounded-lg"
        >
          <div class="space-y-4">
            <div v-for="sec in reportSections" :key="sec.key">
              <h4 class="font-semibold text-slate-800 dark:text-gray-200 mb-1">{{ sec.key }}</h4>
              <pre class="whitespace-pre-wrap break-words text-sm text-slate-700 dark:text-gray-300 bg-slate-50/60 dark:bg-gray-800/40 p-3 rounded">{{ renderSectionValue(sec.value) }}</pre>
            </div>
          </div>
        </NCard>

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
  </NSpin>
</template>

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
import JobBasicInfoCard from './components/JobBasicInfoCard.vue';
import JobCapabilitySidebar from './components/JobCapabilitySidebar.vue';
import PersonJobMatchCard from './components/PersonJobMatchCard.vue';
import CareerReportDrawer from './components/CareerReportDrawer.vue';

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

// 职业发展报告
const careerReport = ref<CareerReportApi.CareerReport | null>(null);
const loadingCareerReport = ref(false);
const generatingCareerReport = ref(false);
const polishingCareerReport = ref(false);
const checkingCareerReport = ref(false);
const showMatch = ref(false);
const showProfile = ref(false);
const careerReportDrawerVisible = ref(false);
const careerReportCheckResult = ref<CareerReportApi.CareerReportCheck | null>(null);

function extractApiError(errLike: any, fallback: string): string {
  const resp = errLike?.response?.data;
  const msg: string = resp?.msg || resp?.message || errLike?.message || fallback;
  const code = resp?.code != null ? `[${resp.code}] ` : '';
  if (resp?.code === 8301) {
    return `${code}${msg}（请稍后重试，或确认后端 LLM 服务可用、能力画像已生成）`;
  }
  return `${code}${msg}`;
}

async function loadJobDetail() {
  if (!jobId.value) { window.$message?.error($t('page.jobs.loadFailed')); router.back(); return; }
  loading.value = true;
  try {
    const { data, error } = await fetchGetJobDetail(jobId.value);
    if (!error && data) {
      jobDetail.value = data;
      // 静默检查是否有已生成的数据，决定按钮文案
      loadCapabilityProfile();
      loadMatchResult();
      loadCareerReport();
    } else {
      window.$message?.error($t('page.jobs.loadFailed'));
      router.back();
    }
  } catch { window.$message?.error($t('page.jobs.loadFailed')); router.back(); }
  finally { loading.value = false; }
}

async function loadCapabilityProfile() {
  if (!jobId.value) return;
  try {
    const { data, error } = await fetchGetJobCapabilityProfile(jobId.value);
    if (!error && data) capabilityProfile.value = data;
  } catch (err) { console.error($t('page.jobs.loadProfileFailed'), err); }
}

async function loadMatchResult() {
  if (!jobId.value) return;
  try {
    const { data, error } = await fetchGetJobStudentMatch(jobId.value);
    if (!error && data) matchResult.value = data;
  } catch { matchResult.value = null; }
}

async function loadCareerReport() {
  if (!jobId.value) return;
  loadingCareerReport.value = true;
  try {
    const { data, error } = await fetchGetLatestCareerReport(jobId.value);
    if (!error && data) careerReport.value = data; else careerReport.value = null;
  } catch { careerReport.value = null; }
  finally { loadingCareerReport.value = false; }
}

async function handleGenerateProfile() {
  if (!jobId.value) return;
  generatingProfile.value = true;
  window.$message?.info($t('page.jobs.profileGenerating') as string, { duration: 5000 });
  try {
    const { data, error } = await fetchGenerateJobCapabilityProfile(jobId.value);
    if (!error && data) { capabilityProfile.value = data; window.$message?.success($t('page.jobs.profileGenerated') as string); }
    else { window.$message?.error(extractApiError(error, $t('page.jobs.createFailed') as string), { duration: 6000 }); }
  } catch (err) {
    window.$message?.error(extractApiError(err, $t('page.jobs.createFailed') + $t('page.jobs.retryLater')), { duration: 6000 });
  } finally { generatingProfile.value = false; }
}

async function handleGenerateMatch() {
  if (!jobId.value) return;
  generatingMatch.value = true;
  window.$message?.info($t('page.jobs.matchAnalyzing') as string, { duration: 5000 });
  try {
    const { data, error } = await fetchGenerateJobStudentMatch(jobId.value);
    if (!error && data) { matchResult.value = data; window.$message?.success($t('page.jobs.matchSuccess') as string); }
    else { window.$message?.error(extractApiError(error, $t('page.jobs.matchFailed') as string), { duration: 6000 }); }
  } catch (err) {
    window.$message?.error(extractApiError(err, $t('page.jobs.matchFailedRetry') as string), { duration: 6000 });
  } finally { generatingMatch.value = false; }
}

async function openMatchSection() {
  if (showMatch.value) { showMatch.value = false; return; }
  showMatch.value = true;
  if (!matchResult.value) {
    await loadMatchResult();
    if (!matchResult.value) await handleGenerateMatch();
  }
}

async function openProfileSection() {
  if (showProfile.value) { showProfile.value = false; return; }
  showProfile.value = true;
  if (!capabilityProfile.value) {
    await loadCapabilityProfile();
    if (!capabilityProfile.value) await handleGenerateProfile();
  }
}

function openCareerReportDrawer() { careerReportDrawerVisible.value = true; loadCareerReport(); }

function isGatewayTimeout(errLike: any): boolean {
  const status = errLike?.response?.status ?? errLike?.status;
  if (status === 504 || status === 502) return true;
  if (errLike?.code === 'ECONNABORTED' || /timeout/i.test(errLike?.message || '')) return true;
  return false;
}

async function pollLatestCareerReport(totalMs = 90_000, intervalMs = 5_000, baseline?: CareerReportApi.CareerReport | null): Promise<CareerReportApi.CareerReport | null> {
  if (!jobId.value) return null;
  const start = Date.now();
  const baselineKey = baseline ? `${baseline.id ?? ''}-${baseline.version ?? ''}` : '';
  while (Date.now() - start < totalMs) {
    await new Promise(r => setTimeout(r, intervalMs));
    try {
      const { data, error } = await fetchGetLatestCareerReport(jobId.value);
      if (!error && data) {
        const currentKey = `${data.id ?? ''}-${data.version ?? ''}`;
        if (currentKey && currentKey !== baselineKey) return data;
      }
    } catch { /* poll retry */ }
  }
  return null;
}

async function handleGenerateCareerReport(payload: CareerReportApi.CareerReportGenerateReq) {
  if (!jobId.value) return;
  generatingCareerReport.value = true;
  const baseline = careerReport.value;
  window.$message?.info($t('page.jobs.careerReport.generating') as string, { duration: 6000 });
  try {
    const { data, error } = await fetchGenerateCareerReport(jobId.value, payload);
    if (!error && data) { careerReport.value = data; careerReportDrawerVisible.value = true; window.$message?.success($t('page.jobs.careerReport.generateSuccess') as string); return; }
    if (isGatewayTimeout(error)) {
      window.$message?.warning($t('page.jobs.careerReport.gatewayTimeoutFallback') as string, { duration: 6000 });
      const polled = await pollLatestCareerReport(120_000, 5_000, baseline);
      if (polled) { careerReport.value = polled; careerReportDrawerVisible.value = true; window.$message?.success($t('page.jobs.careerReport.generateSuccess') as string); }
      else { window.$message?.error($t('page.jobs.careerReport.gatewayTimeoutHint') as string, { duration: 8000 }); }
    } else {
      window.$message?.error(extractApiError(error, $t('page.jobs.careerReport.generateFailed') as string), { duration: 6000 });
    }
  } catch (err) {
    if (isGatewayTimeout(err)) {
      window.$message?.warning($t('page.jobs.careerReport.gatewayTimeoutFallback') as string, { duration: 6000 });
      const polled = await pollLatestCareerReport(120_000, 5_000, baseline);
      if (polled) { careerReport.value = polled; careerReportDrawerVisible.value = true; window.$message?.success($t('page.jobs.careerReport.generateSuccess') as string); }
      else { window.$message?.error($t('page.jobs.careerReport.gatewayTimeoutHint') as string, { duration: 8000 }); }
    } else {
      window.$message?.error(extractApiError(err, $t('page.jobs.careerReport.generateFailed') as string), { duration: 6000 });
    }
  } finally { generatingCareerReport.value = false; }
}

async function handlePolishCareerReport(instruction: string) {
  if (!careerReport.value?.id) return;
  polishingCareerReport.value = true;
  const baseline = careerReport.value;
  window.$message?.info($t('page.jobs.careerReport.polishing') as string, { duration: 5000 });
  const handleFailure = async (errLike: any, fallback: string) => {
    if (isGatewayTimeout(errLike)) {
      window.$message?.warning($t('page.jobs.careerReport.gatewayTimeoutFallback') as string, { duration: 6000 });
      const polled = await pollLatestCareerReport(120_000, 5_000, baseline);
      if (polled) { careerReport.value = polled; window.$message?.success($t('page.jobs.careerReport.polishSuccess') as string); }
      else { window.$message?.error($t('page.jobs.careerReport.gatewayTimeoutHint') as string, { duration: 8000 }); }
    } else { window.$message?.error(extractApiError(errLike, fallback), { duration: 6000 }); }
  };
  try {
    const { data, error } = await fetchPolishCareerReport(careerReport.value.id, { instruction: instruction?.trim() || undefined });
    if (!error && data) { careerReport.value = data; window.$message?.success($t('page.jobs.careerReport.polishSuccess') as string); }
    else { await handleFailure(error, $t('page.jobs.careerReport.polishFailed') as string); }
  } catch (err) { await handleFailure(err, $t('page.jobs.careerReport.polishFailed') as string); }
  finally { polishingCareerReport.value = false; }
}

async function handleCheckCareerReportIntegrity() {
  if (!careerReport.value?.id) return;
  checkingCareerReport.value = true;
  window.$message?.info($t('page.jobs.careerReport.checking') as string, { duration: 4000 });
  try {
    const { data, error } = await fetchCheckCareerReportIntegrity(careerReport.value.id);
    if (!error && data) { careerReportCheckResult.value = data; await loadCareerReport(); window.$message?.success($t('page.jobs.careerReport.checkSuccess') as string); }
    else { window.$message?.error(extractApiError(error, $t('page.jobs.careerReport.checkFailed') as string), { duration: 6000 }); }
  } catch (err) { window.$message?.error(extractApiError(err, $t('page.jobs.careerReport.checkFailed') as string), { duration: 6000 }); }
  finally { checkingCareerReport.value = false; }
}

function handleEdit() { router.push({ name: 'job-edit', query: { id: String(jobId.value) } }); }
function handleBack() { router.back(); }

onMounted(() => {
  if (jobId.value) { loadJobDetail(); }
  else { window.$message?.error($t('page.jobs.formValidation.jobNameRequired')); router.push({ name: 'jobs' }); }
});
</script>

<template>
  <NSpin :show="loading">
    <div class="h-full p-6 bg-[#ffffff] dark:bg-[#181818] min-h-[500px]">
      <!-- 头部 -->
      <div class="mb-6 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <NButton quaternary circle @click="handleBack">
            <template #icon><span>←</span></template>
          </NButton>
          <h1 class="text-2xl font-semibold text-[#222222] dark:text-gray-200">{{ $t('page.jobs.viewDetail') }}</h1>
        </div>
        <div class="flex gap-3">
          <NButton @click="openMatchSection" :loading="generatingMatch">
            {{ showMatch ? '收起匹配分析' : matchResult ? '查看匹配分析' : $t('page.jobs.analyzeMatch') }}
          </NButton>
          <NButton @click="openProfileSection" :loading="generatingProfile">
            {{ showProfile ? '收起能力画像' : capabilityProfile ? '查看能力画像' : $t('page.jobs.generateProfile') }}
          </NButton>
          <NButton type="primary" ghost @click="openCareerReportDrawer" :loading="loadingCareerReport">
            {{ careerReport ? '查看职业报告' : $t('page.jobs.careerReport.entry') }}
          </NButton>
          <NButton type="primary" @click="handleEdit">
            {{ $t('page.jobs.edit') }}
          </NButton>
        </div>
      </div>

      <!-- 人岗匹配（按需显示） -->
      <PersonJobMatchCard v-if="showMatch && matchResult" :match-result="matchResult" @generate-match="handleGenerateMatch" />

      <!-- 双列布局 -->
      <div v-if="jobDetail" class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <JobBasicInfoCard :job-detail="jobDetail" :class="showProfile ? 'lg:col-span-2' : 'lg:col-span-3'" />
        <JobCapabilitySidebar
          v-if="showProfile"
          :capability-profile="capabilityProfile"
          :generating-profile="generatingProfile"
          @generate="handleGenerateProfile"
          class="lg:col-span-1"
        />
      </div>
    </div>

    <!-- 职业发展报告抽屉 -->
    <CareerReportDrawer
      v-model:visible="careerReportDrawerVisible"
      :career-report="careerReport"
      :generating-report="generatingCareerReport"
      :polishing-report="polishingCareerReport"
      :checking-report="checkingCareerReport"
      :check-result="careerReportCheckResult"
      @generate="handleGenerateCareerReport"
      @polish="handlePolishCareerReport"
      @check="handleCheckCareerReportIntegrity"
    />
  </NSpin>
</template>

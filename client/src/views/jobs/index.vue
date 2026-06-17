<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { $t } from '@/locales';
import {
  fetchGetJobPage,
  fetchDeleteJob,
  type JobApi
} from '@/service/api/job';
import { formatJobLocation, isInternJob } from '@/utils/job-display';
import { COMPANY_INDUSTRY_SELECT_OPTIONS } from '@/constants/company-industries';

defineOptions({ name: 'jobs' });

const router = useRouter();

const jobList = ref<JobApi.JobListItem[]>([]);
const loading = ref(false);
const showFilters = ref(false);
const pagination = reactive({
  page: 1,
  limit: 20,
  total: 0
});

const filterForm = reactive({
  jobName: '',
  companyName: '',
  employment: null as JobApi.JobPageQuery['employment'] | null,
  salaryType: null as number | null,
  filterSalaryMin: null as number | null,
  filterSalaryMax: null as number | null,
  industries: [] as string[]
});

const industryOptions = COMPANY_INDUSTRY_SELECT_OPTIONS;

const employmentOptions = computed(() => [
  { label: $t('page.jobs.filterAll'), value: null },
  { label: $t('page.jobs.intern'), value: 'INTERN' as const },
  { label: $t('page.jobs.fulltime'), value: 'FULL_TIME' as const }
]);

const salaryTypeOptions = computed(() => [
  { label: $t('page.jobs.daily'), value: 1 },
  { label: $t('page.jobs.monthly'), value: 2 },
  { label: $t('page.jobs.yearly'), value: 3 }
]);

const hasJobs = computed(() => jobList.value.length > 0);
const salaryFilterEnabled = computed(() => Boolean(filterForm.employment || filterForm.salaryType));

watch(
  () => filterForm.employment,
  value => {
    if (value) {
      filterForm.salaryType = null;
    } else if (filterForm.salaryType == null) {
      filterForm.filterSalaryMin = null;
      filterForm.filterSalaryMax = null;
    }
  }
);

watch(
  () => filterForm.salaryType,
  value => {
    if (value != null) {
      filterForm.employment = null;
    } else if (!filterForm.employment) {
      filterForm.filterSalaryMin = null;
      filterForm.filterSalaryMax = null;
    }
  }
);

function buildQuery(): JobApi.JobPageQuery {
  const query: JobApi.JobPageQuery = {
    page: pagination.page,
    limit: pagination.limit
  };

  const jobName = filterForm.jobName.trim();
  const companyName = filterForm.companyName.trim();
  if (jobName) query.jobName = jobName;
  if (companyName) query.companyName = companyName;
  if (filterForm.employment) query.employment = filterForm.employment;
  if (filterForm.salaryType != null) query.salaryType = filterForm.salaryType;
  if (salaryFilterEnabled.value && filterForm.filterSalaryMin != null) {
    query.filterSalaryMin = filterForm.filterSalaryMin;
  }
  if (salaryFilterEnabled.value && filterForm.filterSalaryMax != null) {
    query.filterSalaryMax = filterForm.filterSalaryMax;
  }

  const industries = filterForm.industries.map(item => item.trim()).filter(Boolean);
  if (industries.length > 0) query.industries = industries;

  return query;
}

function validateSalaryFilter(): boolean {
  if (!salaryFilterEnabled.value) {
    return true;
  }
  const min = filterForm.filterSalaryMin;
  const max = filterForm.filterSalaryMax;
  if (min != null && max != null && min > max) {
    window.$message?.error($t('page.jobs.formValidation.salaryRangeInvalid') as string);
    return false;
  }
  return true;
}

async function loadJobData() {
  loading.value = true;
  try {
    const { data, error } = await fetchGetJobPage(buildQuery());

    if (!error && data) {
      jobList.value = data.list || [];
      pagination.total = data.total || 0;
    }
  } catch {
    window.$message?.error($t('common.requestFailed'));
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  if (!validateSalaryFilter()) {
    return;
  }
  pagination.page = 1;
  loadJobData();
}

function handleResetFilters() {
  filterForm.jobName = '';
  filterForm.companyName = '';
  filterForm.employment = null;
  filterForm.salaryType = null;
  filterForm.filterSalaryMin = null;
  filterForm.filterSalaryMax = null;
  filterForm.industries = [];
  pagination.page = 1;
  loadJobData();
}

function handlePageChange(page: number) {
  pagination.page = page;
  loadJobData();
}

function handleViewDetail(id: number) {
  router.push({ name: 'job-detail', query: { id: String(id) } });
}

function handleCreate() {
  router.push({ name: 'job-edit' });
}

function handleEdit(id: number) {
  router.push({ name: 'job-edit', query: { id } });
}

function handleDelete(id: number) {
  window.$dialog?.warning({
    title: $t('page.jobs.confirmDelete') as string,
    content: $t('page.jobs.confirmDeleteContent') as string,
    positiveText: $t('common.delete') as string,
    negativeText: $t('common.cancel') as string,
    onPositiveClick: async () => {
      loading.value = true;
      const { error } = await fetchDeleteJob(id);
      loading.value = false;
      if (!error) {
        window.$message?.success($t('page.jobs.deleteSuccess') as string);
        loadJobData();
      } else {
        window.$message?.error($t('page.jobs.deleteFailed') as string);
      }
    }
  });
}

function formatEmploymentType(job: JobApi.JobListItem): string {
  return isInternJob(job.salaryType) ? $t('page.jobs.intern') : $t('page.jobs.fulltime');
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

onMounted(() => {
  loadJobData();
});
</script>

<template>
  <NSpin :show="loading">
    <motion.div
      class="nova-page"
      :initial="{ opacity: 0 }"
      :animate="{ opacity: 1 }"
      :transition="{ duration: 0.3 }"
    >
      <div class="nova-page__inner">
      <header class="nova-page__head">
        <div>
          <span class="nova-eyebrow">// roles</span>
          <h1 class="nova-page__title font-display">{{ $t('page.jobs.title') }}</h1>
          <p class="nova-page__sub">解构岗位 JD，生成岗位画像，并与你的能力画像匹配，找到差距与晋升路径。</p>
        </div>
        <div class="flex gap-2">
          <NButton quaternary @click="showFilters = !showFilters">
            <template #icon>
              <icon-carbon-filter class="text-14px" />
            </template>
            {{ $t('page.jobs.toggleFilters') }}
          </NButton>
          <NButton type="primary" @click="handleCreate">
            <template #icon><icon-carbon-add class="text-14px" /></template>
            {{ $t('page.jobs.create') }}
          </NButton>
        </div>
      </header>

      <NCard v-show="showFilters" class="nova-filter-card mb-5" :title="$t('page.jobs.filterTitle')">
        <NGrid :cols="24" :x-gap="16" :y-gap="12">
          <NGridItem :span="8">
            <motion.div class="text-sm mb-1 text-slate-600 dark:text-gray-400">{{ $t('page.jobs.searchJobName') }}</motion.div>
            <NInput v-model:value="filterForm.jobName" clearable :placeholder="$t('page.jobs.searchJobName')" />
          </NGridItem>
          <NGridItem :span="8">
            <motion.div class="text-sm mb-1 text-slate-600 dark:text-gray-400">{{ $t('page.jobs.searchCompanyName') }}</motion.div>
            <NInput v-model:value="filterForm.companyName" clearable :placeholder="$t('page.jobs.searchCompanyName')" />
          </NGridItem>
          <NGridItem :span="8">
            <motion.div class="text-sm mb-1 text-slate-600 dark:text-gray-400">{{ $t('page.jobs.companyIndustry') }}</motion.div>
            <NSelect
              v-model:value="filterForm.industries"
              :options="industryOptions"
              multiple
              filterable
              clearable
              max-tag-count="responsive"
              :placeholder="$t('page.jobs.placeholders.industriesSelect')"
            />
          </NGridItem>
          <NGridItem :span="8">
            <motion.div class="text-sm mb-1 text-slate-600 dark:text-gray-400">{{ $t('page.jobs.employmentType') }}</motion.div>
            <NSelect v-model:value="filterForm.employment" :options="employmentOptions" clearable />
          </NGridItem>
          <NGridItem :span="8">
            <motion.div class="text-sm mb-1 text-slate-600 dark:text-gray-400">{{ $t('page.jobs.salaryType') }}</motion.div>
            <NSelect
              v-model:value="filterForm.salaryType"
              :options="salaryTypeOptions"
              clearable
              :disabled="Boolean(filterForm.employment)"
              :placeholder="$t('page.jobs.salaryTypePlaceholder')"
            />
          </NGridItem>
          <NGridItem :span="8">
            <motion.div class="text-sm mb-1 text-slate-600 dark:text-gray-400">{{ $t('page.jobs.salaryMin') }}</motion.div>
            <NInputNumber
              v-model:value="filterForm.filterSalaryMin"
              class="w-full"
              :min="0"
              :disabled="!salaryFilterEnabled"
              :placeholder="$t('page.jobs.salaryMin')"
            />
          </NGridItem>
          <NGridItem :span="8">
            <motion.div class="text-sm mb-1 text-slate-600 dark:text-gray-400">{{ $t('page.jobs.salaryMax') }}</motion.div>
            <NInputNumber
              v-model:value="filterForm.filterSalaryMax"
              class="w-full"
              :min="0"
              :disabled="!salaryFilterEnabled"
              :placeholder="$t('page.jobs.salaryMax')"
            />
          </NGridItem>
        </NGrid>
        <motion.div class="mt-2 text-xs text-slate-500 dark:text-gray-500">{{ $t('page.jobs.salaryFilterHint') }}</motion.div>
        <motion.div class="mt-4 flex gap-2">
          <NButton type="primary" @click="handleSearch">{{ $t('common.search') }}</NButton>
          <NButton @click="handleResetFilters">{{ $t('common.reset') }}</NButton>
        </motion.div>
      </NCard>

      <motion.div v-if="hasJobs" class="flex flex-col gap-3">
        <article
          v-for="job in jobList"
          :key="job.id"
          class="nova-job-card"
        >
          <div class="nova-job-card__main">
            <div class="nova-job-card__head">
              <h3 class="nova-job-card__title">{{ job.jobName }}</h3>
              <NTag :type="isInternJob(job.salaryType) ? 'warning' : 'success'" size="small" round>
                {{ formatEmploymentType(job) }}
              </NTag>
              <NTag v-if="job.companyIndustries && job.companyIndustries.length > 0" type="info" size="small" round>
                {{ job.companyIndustries[0] }}
              </NTag>
            </div>

            <div class="nova-job-card__meta">
              <span class="nova-job-card__meta-item">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 21V7l9-4 9 4v14" />
                  <path d="M9 21V12h6v9" />
                  <path d="M3 21h18" />
                </svg>
                <span>{{ job.companyName }}</span>
              </span>
              <span class="nova-job-card__meta-item">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 22s7-7.5 7-13a7 7 0 0 0-14 0c0 5.5 7 13 7 13Z" />
                  <circle cx="12" cy="9" r="2.5" />
                </svg>
                <span>{{ formatJobLocation(job.location) }}</span>
              </span>
              <span class="nova-job-card__salary">
                {{ formatSalary(job) }}
              </span>
            </div>

            <p class="nova-job-card__desc">{{ job.description }}</p>

            <div v-if="job.keywords && job.keywords.length > 0" class="flex items-center gap-1.5 flex-wrap">
              <NTag
                v-for="keyword in job.keywords.slice(0, 6)"
                :key="keyword"
                size="tiny"
                round
                :bordered="false"
                class="nova-keyword"
              >
                {{ keyword }}
              </NTag>
            </div>
          </div>

          <div class="nova-job-card__actions">
            <NButton size="small" tertiary @click="handleViewDetail(job.id)">
              <template #icon>
                <icon-carbon-view class="text-14px" />
              </template>
              {{ $t('page.jobs.viewDetail') }}
            </NButton>
            <NButton size="small" secondary type="primary" @click="handleEdit(job.id)">
              <template #icon>
                <icon-carbon-edit class="text-14px" />
              </template>
              {{ $t('page.jobs.edit') }}
            </NButton>
            <NButton size="small" ghost type="error" @click="handleDelete(job.id)">
              <template #icon>
                <icon-carbon-trash-can class="text-14px" />
              </template>
              {{ $t('page.jobs.delete') }}
            </NButton>
          </div>
        </article>
      </motion.div>

      <motion.div v-else-if="!loading" class="nova-empty">
        <span class="nova-empty__icon">
          <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
            <path d="M3 21V7l9-4 9 4v14" />
            <path d="M9 21V12h6v9" />
          </svg>
        </span>
        <p class="nova-empty__text">{{ $t('page.jobs.empty') }}</p>
        <NButton type="primary" size="medium" @click="handleCreate">
          <template #icon>
            <icon-carbon-add class="text-14px" />
          </template>
          {{ $t('page.jobs.createFirst') }}
        </NButton>
      </motion.div>

      <motion.div v-if="hasJobs" class="mt-6 flex justify-center">
        <NPagination
          v-model:page="pagination.page"
          :page-size="pagination.limit"
          :item-count="pagination.total"
          show-size-picker
          :page-sizes="[10, 20, 50, 100]"
          @update:page="handlePageChange"
          @update:page-size="(size: number) => { pagination.limit = size; handleSearch(); }"
        />
      </motion.div>
      </div>
    </motion.div>
  </NSpin>
</template>

<style scoped>
.nova-page {
  position: relative;
  min-height: 100%;
  padding: 32px 32px 60px;
  color: var(--nova-text);
}

.nova-page__inner {
  max-width: 1080px;
  margin: 0 auto;
}

.nova-page__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.nova-page__title {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(120deg, #fff 0%, #c9c4ff 60%, #93f1ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.nova-page__sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--nova-text-faded);
  max-width: 640px;
  line-height: 1.6;
}

.nova-eyebrow {
  display: inline-block;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--nova-violet);
  text-transform: lowercase;
}

:deep(.nova-filter-card) {
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.015)) !important;
  border: 1px solid var(--nova-border) !important;
  backdrop-filter: blur(20px);
}

.nova-job-card {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 18px 20px;
  border-radius: 14px;
  border: 1px solid var(--nova-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0.018));
  backdrop-filter: blur(20px) saturate(1.05);
  transition: border-color 0.3s ease, transform 0.25s ease;
}

.nova-job-card:hover {
  border-color: var(--nova-border-strong);
  transform: translateY(-1px);
}

.nova-job-card__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nova-job-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.nova-job-card__title {
  font-size: 17px;
  font-weight: 600;
  color: #fff;
  letter-spacing: -0.005em;
  margin: 0;
}

.nova-job-card__meta {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 12.5px;
  color: var(--nova-text-faded);
}

.nova-job-card__meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.nova-job-card__salary {
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--nova-mint);
  font-family: 'JetBrains Mono', monospace;
  letter-spacing: 0.01em;
}

.nova-job-card__desc {
  margin: 0;
  font-size: 13px;
  color: var(--nova-text-soft);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

:deep(.nova-keyword) {
  background: rgba(124, 92, 255, 0.10) !important;
  color: var(--nova-text-soft) !important;
  font-size: 11px !important;
}

.nova-job-card__actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex-shrink: 0;
}

.nova-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  border-radius: 18px;
  border: 1px dashed var(--nova-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.025), rgba(255, 255, 255, 0.008));
  text-align: center;
  gap: 14px;
}

.nova-empty__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: rgba(124, 92, 255, 0.1);
  border: 1px solid rgba(124, 92, 255, 0.22);
  color: var(--nova-violet);
}

.nova-empty__text {
  color: var(--nova-text-faded);
  font-size: 13.5px;
  margin: 0;
}
</style>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { COMPANY_INDUSTRY_SELECT_OPTIONS } from '@/constants/company-industries';
import { type JobApi, fetchDeleteJob, fetchGetJobPage } from '@/service/api/job';
import { formatJobLocation, isInternJob } from '@/utils/job-display';
import { $t } from '@/locales';

defineOptions({ name: 'Jobs' });

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
    } else if (filterForm.salaryType === null || filterForm.salaryType === undefined) {
      filterForm.filterSalaryMin = null;
      filterForm.filterSalaryMax = null;
    }
  }
);

watch(
  () => filterForm.salaryType,
  value => {
    if (value !== null && value !== undefined) {
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
  if (filterForm.salaryType !== null && filterForm.salaryType !== undefined) query.salaryType = filterForm.salaryType;
  if (salaryFilterEnabled.value && filterForm.filterSalaryMin !== null && filterForm.filterSalaryMin !== undefined) {
    query.filterSalaryMin = filterForm.filterSalaryMin;
  }
  if (salaryFilterEnabled.value && filterForm.filterSalaryMax !== null && filterForm.filterSalaryMax !== undefined) {
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
  if (min !== null && min !== undefined && max !== null && max !== undefined && min > max) {
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
    <div class="jobs-page h-full min-h-[500px] bg-slate-50/50 p-6 dark:bg-dark-100">
      <div class="mb-6 flex items-center justify-between">
        <h1 class="text-2xl text-slate-800 font-bold dark:text-gray-200">{{ $t('page.jobs.title') }}</h1>
        <div class="flex gap-2">
          <NButton quaternary @click="showFilters = !showFilters">
            {{ $t('page.jobs.toggleFilters') }}
          </NButton>
          <NButton type="primary" @click="handleCreate">
            <template #icon><span>+</span></template>
            {{ $t('page.jobs.create') }}
          </NButton>
        </div>
      </div>

      <NCard v-show="showFilters" class="mb-6 rounded-xl" :title="$t('page.jobs.filterTitle')">
        <NGrid :cols="24" :x-gap="16" :y-gap="12">
          <NGridItem :span="8">
            <div class="mb-1 text-sm text-slate-600 dark:text-gray-400">{{ $t('page.jobs.searchJobName') }}</div>
            <NInput v-model:value="filterForm.jobName" clearable :placeholder="$t('page.jobs.searchJobName')" />
          </NGridItem>
          <NGridItem :span="8">
            <div class="mb-1 text-sm text-slate-600 dark:text-gray-400">{{ $t('page.jobs.searchCompanyName') }}</div>
            <NInput v-model:value="filterForm.companyName" clearable :placeholder="$t('page.jobs.searchCompanyName')" />
          </NGridItem>
          <NGridItem :span="8">
            <div class="mb-1 text-sm text-slate-600 dark:text-gray-400">{{ $t('page.jobs.companyIndustry') }}</div>
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
            <div class="mb-1 text-sm text-slate-600 dark:text-gray-400">{{ $t('page.jobs.employmentType') }}</div>
            <NSelect v-model:value="filterForm.employment" :options="employmentOptions" clearable />
          </NGridItem>
          <NGridItem :span="8">
            <div class="mb-1 text-sm text-slate-600 dark:text-gray-400">{{ $t('page.jobs.salaryType') }}</div>
            <NSelect
              v-model:value="filterForm.salaryType"
              :options="salaryTypeOptions"
              clearable
              :disabled="Boolean(filterForm.employment)"
              :placeholder="$t('page.jobs.salaryTypePlaceholder')"
            />
          </NGridItem>
          <NGridItem :span="8">
            <div class="mb-1 text-sm text-slate-600 dark:text-gray-400">{{ $t('page.jobs.salaryMin') }}</div>
            <NInputNumber
              v-model:value="filterForm.filterSalaryMin"
              class="w-full"
              :min="0"
              :disabled="!salaryFilterEnabled"
              :placeholder="$t('page.jobs.salaryMin')"
            />
          </NGridItem>
          <NGridItem :span="8">
            <div class="mb-1 text-sm text-slate-600 dark:text-gray-400">{{ $t('page.jobs.salaryMax') }}</div>
            <NInputNumber
              v-model:value="filterForm.filterSalaryMax"
              class="w-full"
              :min="0"
              :disabled="!salaryFilterEnabled"
              :placeholder="$t('page.jobs.salaryMax')"
            />
          </NGridItem>
        </NGrid>
        <div class="mt-2 text-xs text-slate-500 dark:text-gray-500">{{ $t('page.jobs.salaryFilterHint') }}</div>
        <div class="mt-4 flex gap-2">
          <NButton type="primary" @click="handleSearch">{{ $t('common.search') }}</NButton>
          <NButton @click="handleResetFilters">{{ $t('common.reset') }}</NButton>
        </div>
      </NCard>

      <div v-if="hasJobs" class="space-y-4">
        <NCard
          v-for="job in jobList"
          :key="job.id"
          hoverable
          class="border-slate-200 rounded-xl transition-all dark:border-gray-700 hover:shadow-md"
        >
          <div class="flex items-start justify-between">
            <div class="flex-1">
              <div class="mb-2 flex items-center gap-3">
                <h3 class="text-xl text-slate-800 font-bold dark:text-gray-200">{{ job.jobName }}</h3>
                <NTag :type="isInternJob(job.salaryType) ? 'warning' : 'success'" size="small">
                  {{ formatEmploymentType(job) }}
                </NTag>
                <NTag v-if="job.companyIndustries && job.companyIndustries.length > 0" type="info" size="small">
                  {{ job.companyIndustries[0] }}
                </NTag>
              </div>

              <div class="mb-3 flex items-center gap-4 text-sm text-slate-600 dark:text-gray-400">
                <div class="flex items-center gap-1">
                  <span class="text-base">🏢</span>
                  <span>{{ job.companyName }}</span>
                </div>
                <div class="flex items-center gap-1">
                  <span class="text-base">📍</span>
                  <span>{{ formatJobLocation(job.location) }}</span>
                </div>
                <div class="flex items-center gap-1">
                  <span class="text-base">¥</span>
                  <span class="text-orange-600 font-semibold">{{ formatSalary(job) }}</span>
                </div>
              </div>

              <p class="line-clamp-2 mb-3 text-sm text-slate-600 dark:text-gray-400">{{ job.description }}</p>

              <div v-if="job.keywords && job.keywords.length > 0" class="flex items-center gap-2">
                <NTag v-for="keyword in job.keywords.slice(0, 5)" :key="keyword" type="default" size="small" round>
                  {{ keyword }}
                </NTag>
              </div>
            </div>

            <div class="ml-4 flex gap-2">
              <NButton size="small" @click="handleViewDetail(job.id)">
                <template #icon><span>👁</span></template>
                {{ $t('page.jobs.viewDetail') }}
              </NButton>
              <NButton size="small" type="primary" @click="handleEdit(job.id)">
                <template #icon><span>✏️</span></template>
                {{ $t('page.jobs.edit') }}
              </NButton>
              <NButton size="small" type="error" @click="handleDelete(job.id)">
                <template #icon><span>🗑</span></template>
                {{ $t('page.jobs.delete') }}
              </NButton>
            </div>
          </div>
        </NCard>
      </div>

      <div v-else-if="!loading" class="flex flex-col items-center justify-center py-16">
        <NEmpty :description="$t('page.jobs.empty')">
          <template #extra>
            <NButton type="primary" size="large" @click="handleCreate">
              <template #icon><span>＋</span></template>
              {{ $t('page.jobs.createFirst') }}
            </NButton>
          </template>
        </NEmpty>
      </div>

      <div v-if="hasJobs" class="mt-6 flex justify-center">
        <NPagination
          v-model:page="pagination.page"
          :page-size="pagination.limit"
          :item-count="pagination.total"
          show-size-picker
          :page-sizes="[10, 20, 50, 100]"
          @update:page="handlePageChange"
          @update:page-size="
            (size: number) => {
              pagination.limit = size;
              handleSearch();
            }
          "
        />
      </div>
    </div>
  </NSpin>
</template>

<style scoped>
.jobs-page {
  animation: jobs-fade-in 0.3s ease;
}

@keyframes jobs-fade-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>

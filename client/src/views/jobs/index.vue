<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { $t } from '@/locales';
import {
  fetchGetJobPage,
  fetchDeleteJob,
  type JobApi
} from '@/service/api/job';

defineOptions({ name: 'jobs' });

const router = useRouter();

// --- 基础状态 ---
const jobList = ref<JobApi.JobListItem[]>([]);
const loading = ref(false);
const pagination = reactive({
  page: 1,
  limit: 20,
  total: 0
});

// --- 查询条件 ---
const searchForm = reactive<JobApi.JobPageQuery>({
  page: 1,
  limit: 20,
  jobName: '',
  companyName: '',
  employment: undefined,
  filterSalaryMin: undefined,
  filterSalaryMax: undefined
});

const showSearchPanel = ref(false);

// --- 薪资类型选项 ---
const salaryTypeOptions = [
  { label: $t('page.jobs.daily') as string, value: 1 },
  { label: $t('page.jobs.monthly') as string, value: 2 },
  { label: $t('page.jobs.yearly') as string, value: 3 }
];

const employmentOptions = [
  { label: $t('page.jobs.intern') as string, value: 'INTERN' },
  { label: $t('page.jobs.fulltime') as string, value: 'FULLTIME' }
];

// --- 计算属性 ---
const hasJobs = computed(() => jobList.value.length > 0);

// --- 方法 ---
async function loadJobData() {
  loading.value = true;
  try {
    searchForm.page = pagination.page;
    searchForm.limit = pagination.limit;

    const { data, error } = await fetchGetJobPage(searchForm);

    if (!error && data) {
      // 后端返回的是 list 字段，不是 records
      jobList.value = data.list || [];
      pagination.total = data.total || 0;
    }
  } catch (err) {
    window.$message?.error($t('common.requestFailed'));
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pagination.page = 1;
  loadJobData();
}

function handleReset() {
  searchForm.jobName = '';
  searchForm.companyName = '';
  searchForm.employment = undefined;
  searchForm.filterSalaryMin = undefined;
  searchForm.filterSalaryMax = undefined;
  pagination.page = 1;
  loadJobData();
}

function handlePageChange(page: number) {
  pagination.page = page;
  loadJobData();
}

function handleViewDetail(id: number) {
  router.push({ name: 'job-detail', params: { id } });
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

function formatSalary(job: JobApi.JobListItem): string {
  const typeLabel = salaryTypeOptions.find(t => t.value === job.salaryType)?.label || '';
  return `${job.salaryMin}-${job.salaryMax} ${typeLabel}`;
}

onMounted(() => {
  loadJobData();
});
</script>

<template>
  <NSpin :show="loading">
    <div class="h-full p-6 bg-slate-50/50 min-h-[500px]">
      <div class="mb-6 flex justify-between items-center">
        <h1 class="text-2xl font-bold text-slate-800">{{ $t('page.jobs.title') }}</h1>
        <NButton type="primary" @click="handleCreate">
          <template #icon><div class="i-mdi-plus"></div></template>
          {{ $t('page.jobs.create') }}
        </NButton>
      </div>

      <!-- 搜索区域 -->
      <NCard class="mb-4">
        <div class="flex items-center gap-3">
          <NInput
            v-model:value="searchForm.jobName"
            :placeholder="$t('page.jobs.searchJobName')"
            clearable
            class="w-48"
            @keyup.enter="handleSearch"
          />
          <NInput
            v-model:value="searchForm.companyName"
            :placeholder="$t('page.jobs.searchCompanyName')"
            clearable
            class="w-48"
            @keyup.enter="handleSearch"
          />
          <NSelect
            v-model:value="searchForm.employment"
            :placeholder="$t('page.jobs.employmentType')"
            clearable
            :options="employmentOptions"
            class="w-32"
          />
          <NButton type="primary" @click="handleSearch">
            <template #icon><div class="i-mdi-magnify"></div></template>
            {{ $t('common.search') }}
          </NButton>
          <NButton @click="handleReset">{{ $t('common.reset') }}</NButton>
        </div>
      </NCard>

      <!-- 岗位列表 -->
      <div v-if="hasJobs" class="space-y-4">
        <NCard
          v-for="job in jobList"
          :key="job.id"
          hoverable
          class="rounded-xl border-slate-200 transition-all hover:shadow-md"
        >
          <div class="flex justify-between items-start">
            <div class="flex-1">
              <div class="flex items-center gap-3 mb-2">
                <h3 class="text-xl font-bold text-slate-800">{{ job.jobName }}</h3>
                <NTag v-if="job.companyIndustries && job.companyIndustries.length > 0" type="info" size="small">
                  {{ job.companyIndustries[0] }}
                </NTag>
              </div>

              <div class="flex items-center gap-4 text-sm text-slate-600 mb-3">
                <div class="flex items-center gap-1">
                  <div class="i-mdi-office-building text-base"></div>
                  <span>{{ job.companyName }}</span>
                </div>
                <div class="flex items-center gap-1">
                  <div class="i-mdi-map-marker text-base"></div>
                  <span>{{ job.location }}</span>
                </div>
                <div class="flex items-center gap-1">
                  <div class="i-mdi-currency-cny text-base"></div>
                  <span class="text-orange-600 font-semibold">{{ formatSalary(job) }}</span>
                </div>
              </div>

              <p class="text-slate-600 text-sm line-clamp-2 mb-3">{{ job.description }}</p>

              <div v-if="job.keywords && job.keywords.length > 0" class="flex items-center gap-2">
                <NTag
                  v-for="keyword in job.keywords.slice(0, 5)"
                  :key="keyword"
                  type="default"
                  size="small"
                  round
                >
                  {{ keyword }}
                </NTag>
              </div>
            </div>

            <div class="flex gap-2 ml-4">
              <NButton size="small" @click="handleViewDetail(job.id)">
                <template #icon><div class="i-mdi-eye"></div></template>
                {{ $t('page.jobs.viewDetail') }}
              </NButton>
              <NButton size="small" type="primary" @click="handleEdit(job.id)">
                <template #icon><div class="i-mdi-pencil"></div></template>
                {{ $t('page.jobs.edit') }}
              </NButton>
              <NButton size="small" type="error" @click="handleDelete(job.id)">
                <template #icon><div class="i-mdi-delete"></div></template>
                {{ $t('page.jobs.delete') }}
              </NButton>
            </div>
          </div>
        </NCard>
      </div>

      <!-- 空状态 -->
      <div v-else-if="!loading" class="flex flex-col items-center justify-center py-16">
        <NEmpty :description="$t('page.jobs.empty')">
          <template #extra>
            <NButton type="primary" size="large" @click="handleCreate">
              <template #icon><div class="i-mdi-plus-box-outline"></div></template>
              {{ $t('page.jobs.createFirst') }}
            </NButton>
          </template>
        </NEmpty>
      </div>

      <!-- 分页 -->
      <div v-if="hasJobs" class="mt-6 flex justify-center">
        <NPagination
          v-model:page="pagination.page"
          :page-count="Math.ceil(pagination.total / pagination.limit)"
          :page-size="pagination.limit"
          :item-count="pagination.total"
          show-size-picker
          :page-sizes="[10, 20, 50, 100]"
          @update:page="handlePageChange"
          @update:page-size="(size: number) => { pagination.limit = size; loadJobData(); }"
        />
      </div>
    </div>
  </NSpin>
</template>

<style scoped>
.line-clamp-2 {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>

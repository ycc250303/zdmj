<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { $t } from '@/locales';
import { fetchGetJobPage, fetchDeleteJob, type JobApi } from '@/service/api/job';

defineOptions({ name: 'jobs' });

const router = useRouter();
const jobList = ref<JobApi.JobListItem[]>([]);
const loading = ref(false);
const pagination = reactive({ page: 1, limit: 20, total: 0 });
const searchForm = reactive<JobApi.JobPageQuery>({ page: 1, limit: 20 });
const hasJobs = computed(() => jobList.value.length > 0);

async function loadJobData() {
  loading.value = true;
  try {
    searchForm.page = pagination.page; searchForm.limit = pagination.limit;
    const { data, error } = await fetchGetJobPage(searchForm);
    if (!error && data) { jobList.value = data.list || []; pagination.total = data.total || 0; }
  } catch { window.$message?.error($t('common.requestFailed')); }
  finally { loading.value = false; }
}

function handlePageChange(page: number) { pagination.page = page; loadJobData(); }
function handleViewDetail(id: number) { router.push({ name: 'job-detail', query: { id: String(id) } }); }
function handleCreate() { router.push({ name: 'job-edit' }); }
function handleEdit(id: number) { router.push({ name: 'job-edit', query: { id } }); }
function handleDelete(id: number) {
  window.$dialog?.warning({
    title: $t('page.jobs.confirmDelete') as string,
    content: $t('page.jobs.confirmDeleteContent') as string,
    positiveText: $t('common.delete') as string,
    negativeText: $t('common.cancel') as string,
    onPositiveClick: async () => {
      const { error } = await fetchDeleteJob(id);
      if (!error) { window.$message?.success($t('page.jobs.deleteSuccess') as string); loadJobData(); }
      else { window.$message?.error($t('page.jobs.deleteFailed') as string); }
    }
  });
}

function formatSalary(job: JobApi.JobListItem): string {
  const typeMap: Record<number, string> = { 1: $t('page.jobs.daily'), 2: $t('page.jobs.monthly'), 3: $t('page.jobs.yearly') };
  const typeLabel = typeMap[job.salaryType as number] || '';
  return `${job.salaryMin}-${job.salaryMax} ${typeLabel}`;
}

onMounted(() => { loadJobData(); });
</script>

<template>
  <div class="jobs-root">
    <!-- Header -->
    <div class="jobs-header">
      <div>
        <h1 class="jobs-title">{{ $t('page.jobs.title') }}</h1>
        <p class="jobs-sub">发现最适合你的职业机会</p>
      </div>
      <button class="btn-glass-dark" @click="handleCreate">
        + {{ $t('page.jobs.create') }}
      </button>
    </div>

    <!-- Job Cards Grid -->
    <NSpin :show="loading">
      <div v-if="hasJobs" class="jobs-grid">
        <article
          v-for="job in jobList"
          :key="job.id"
          class="job-card"
          @click="handleViewDetail(job.id)"
        >
          <div class="job-card-top">
            <div class="job-card-company">
              <div class="company-avatar">{{ (job.companyName || '?')[0] }}</div>
              <div>
                <h3 class="job-name">{{ job.jobName }}</h3>
                <span class="job-company">{{ job.companyName }} · {{ job.location }}</span>
              </div>
            </div>
            <span class="job-salary">{{ formatSalary(job) }}</span>
          </div>

          <p class="job-desc">{{ job.description }}</p>

          <div v-if="job.keywords?.length" class="job-tags">
            <span v-for="kw in job.keywords.slice(0, 5)" :key="kw" class="job-tag">{{ kw }}</span>
          </div>

          <div class="job-card-actions" @click.stop>
            <button class="btn-glass" @click="handleViewDetail(job.id)">查看</button>
            <button class="btn-glass" @click="handleEdit(job.id)">编辑</button>
            <NButton size="small" type="error" ghost @click="handleDelete(job.id)">删除</NButton>
          </div>
        </article>
      </div>

      <!-- Empty -->
      <div v-else-if="!loading" class="jobs-empty">
        <div class="empty-icon">📋</div>
        <p class="empty-text">{{ $t('page.jobs.empty') }}</p>
        <button class="btn-glass-dark" @click="handleCreate">+ {{ $t('page.jobs.createFirst') }}</button>
      </div>

      <!-- Pagination -->
      <div v-if="hasJobs" class="jobs-pager">
        <NPagination
          v-model:page="pagination.page" :page-size="pagination.limit"
          :item-count="pagination.total" show-size-picker :page-sizes="[10,20,50,100]"
          @update:page="handlePageChange"
          @update:page-size="(size: number) => { pagination.limit = size; loadJobData(); }"
        />
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.jobs-root { min-height: 100%; background: #fafafa; padding: 32px 40px; }

.jobs-header {
  display: flex; justify-content: space-between; align-items: flex-start;
  margin-bottom: 28px; max-width: 1200px; margin-left: auto; margin-right: auto;
}
.jobs-title { font-size: 28px; font-weight: 700; color: #222; margin: 0 0 4px; }
.jobs-sub { font-size: 14px; color: #6a6a6a; margin: 0; }

.jobs-grid { max-width: 1200px; margin: 0 auto; display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; }

.job-card {
  background: #fff; border: 1px solid #ebebeb; border-radius: 14px; padding: 24px;
  cursor: pointer; transition: all 0.3s cubic-bezier(0.16,1,0.3,1);
  display: flex; flex-direction: column; gap: 14px;
  animation: cardIn 0.4s ease both;
}
@keyframes cardIn { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: translateY(0); } }

.job-card:hover { transform: translateY(-4px); box-shadow: 0 8px 30px rgba(0,0,0,0.06); border-color: rgba(255,56,92,0.15); }

.job-card-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.job-card-company { display: flex; align-items: center; gap: 12px; flex: 1; min-width: 0; }
.company-avatar {
  width: 40px; height: 40px; border-radius: 10px; background: linear-gradient(135deg, #ff385c, #ff6b81);
  color: #fff; display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 18px; flex-shrink: 0;
}
.job-name { font-size: 15px; font-weight: 600; color: #222; margin: 0 0 2px; }
.job-company { font-size: 12px; color: #6a6a6a; }
.job-salary { font-size: 14px; font-weight: 600; color: #ff385c; white-space: nowrap; }

.job-desc {
  font-size: 13px; color: #3f3f3f; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.job-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.job-tag {
  font-size: 11px; padding: 3px 10px; border-radius: 99px;
  background: #f7f7f7; color: #6a6a6a; font-weight: 500;
}
.job-card-actions { display: flex; gap: 8px; padding-top: 4px; border-top: 1px solid #f0f0f0; }

.jobs-empty { text-align: center; padding: 80px 24px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; opacity: 0.6; }
.empty-text { font-size: 16px; color: #6a6a6a; margin: 0 0 20px; }

.jobs-pager { display: flex; justify-content: center; margin-top: 32px; }

@media (max-width: 640px) {
  .jobs-root { padding: 20px 16px; }
  .jobs-grid { grid-template-columns: 1fr; }
}
</style>

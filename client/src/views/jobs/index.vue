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
  limit: 20
});

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
      jobList.value = data.list || [];
      pagination.total = data.total || 0;
    }
  } catch (err) {
    window.$message?.error($t('common.requestFailed'));
  } finally {
    loading.value = false;
  }
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

function formatSalary(job: JobApi.JobListItem): string {
  const typeMap = {
    1: $t('page.jobs.daily'),
    2: $t('page.jobs.monthly'),
    3: $t('page.jobs.yearly')
  };
  const typeLabel = typeMap[job.salaryType as keyof typeof typeMap] || '';
  return `${job.salaryMin}-${job.salaryMax} ${typeLabel}`;
}

function pad(n: number) {
  return String(n).padStart(2, '0');
}

onMounted(() => {
  loadJobData();
});
</script>

<template>
  <NSpin :show="loading">
    <div class="editorial-jobs">
      <div class="grain-bg paper-grain"></div>

      <!-- 章节头 -->
      <header class="section-head">
        <div class="head-meta">
          <span class="head-bar"></span>
          <span class="head-tag">— SECTION 04 / OPPORTUNITIES —</span>
        </div>
        <div class="head-row">
          <h1 class="head-title font-display">{{ $t('page.jobs.title') }}</h1>
          <button class="primary-btn" @click="handleCreate">
            <span class="plus">+</span>
            {{ $t('page.jobs.create') }}
          </button>
        </div>
        <p class="head-byline">— A curated list of openings, updated regularly.</p>
      </header>

      <div class="head-rule"></div>

      <!-- 岗位条目（一行式） -->
      <section v-if="hasJobs" class="jobs-list">
        <article
          v-for="(job, idx) in jobList"
          :key="job.id"
          class="job-row"
          @click="handleViewDetail(job.id)"
        >
          <div class="job-no font-display">{{ pad(idx + 1 + (pagination.page - 1) * pagination.limit) }}</div>

          <div class="job-content">
            <div class="job-top">
              <h3 class="job-title font-display">{{ job.jobName }}</h3>
              <NTag
                v-if="job.companyIndustries && job.companyIndustries.length > 0"
                :bordered="false"
                size="small"
                class="industry-tag"
              >
                {{ job.companyIndustries[0] }}
              </NTag>
            </div>

            <div class="job-meta">
              <span class="meta-i">{{ job.companyName }}</span>
              <span class="dot">·</span>
              <span class="meta-i">{{ job.location }}</span>
              <span class="dot">·</span>
              <span class="salary">{{ formatSalary(job) }}</span>
            </div>

            <p class="job-desc">{{ job.description }}</p>

            <div v-if="job.keywords && job.keywords.length > 0" class="kw-row">
              <span
                v-for="keyword in job.keywords.slice(0, 5)"
                :key="keyword"
                class="kw-chip"
              >
                {{ keyword }}
              </span>
            </div>
          </div>

          <div class="job-actions" @click.stop>
            <button class="row-btn" @click="handleViewDetail(job.id)">
              {{ $t('page.jobs.viewDetail') }}
              <span class="arrow">→</span>
            </button>
            <button class="row-btn ghost" @click="handleEdit(job.id)">
              {{ $t('page.jobs.edit') }}
            </button>
            <button class="row-btn danger" @click="handleDelete(job.id)">
              {{ $t('page.jobs.delete') }}
            </button>
          </div>
        </article>
      </section>

      <!-- 空状态 -->
      <div v-else-if="!loading" class="empty-block">
        <div class="empty-no font-display">∅</div>
        <p class="empty-headline font-display">No stories yet.</p>
        <p class="empty-sub">{{ $t('page.jobs.empty') }}</p>
        <button class="primary-btn lg" @click="handleCreate">
          <span class="plus">+</span>
          {{ $t('page.jobs.createFirst') }}
        </button>
      </div>

      <!-- 分页 -->
      <div v-if="hasJobs" class="pager">
        <NPagination
          v-model:page="pagination.page"
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
.editorial-jobs {
  position: relative;
  min-height: 500px;
  height: 100%;
  padding: 40px 56px 56px;
  background: var(--brand-cream);
  color: var(--brand-ink);
}

.grain-bg {
  position: absolute;
  inset: 0;
  opacity: 0.4;
  pointer-events: none;
}

/* ============ Head ============ */
.section-head {
  position: relative;
  z-index: 1;
  margin-bottom: 16px;
}
.head-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.head-bar {
  width: 40px;
  height: 2px;
  background: var(--brand-mocha);
}
.head-tag {
  font-size: 11px;
  letter-spacing: 0.22em;
  color: var(--brand-ink-soft);
}
.head-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}
.head-title {
  font-size: clamp(36px, 4vw, 56px);
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1;
}
.head-byline {
  margin-top: 12px;
  font-family: var(--serif-display);
  font-style: italic;
  color: var(--brand-ink-soft);
  font-size: 15px;
}
.head-rule {
  position: relative;
  height: 1px;
  background: var(--brand-line);
  margin: 28px 0 4px;
}
.head-rule::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 4px;
  height: 1px;
  background: var(--brand-line);
}

/* 主按钮 */
.primary-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 999px;
  background: var(--brand-ink);
  color: var(--brand-paper);
  border: none;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}
.primary-btn:hover {
  background: var(--brand-mocha-deep);
  transform: translateY(-1px);
}
.primary-btn .plus {
  font-size: 16px;
  margin-right: 2px;
}
.primary-btn.lg {
  padding: 14px 28px;
  font-size: 15px;
}

/* ============ Job rows ============ */
.jobs-list {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
}

.job-row {
  position: relative;
  display: grid;
  grid-template-columns: 56px 1fr auto;
  gap: 24px;
  padding: 28px 0;
  border-bottom: 1px solid var(--brand-line);
  cursor: pointer;
  transition: background 0.3s ease;
}

.job-row:hover {
  background: rgba(184, 107, 75, 0.03);
}

.job-no {
  font-size: 24px;
  font-style: italic;
  color: var(--brand-mocha);
  letter-spacing: 0.02em;
  padding-top: 4px;
}

.job-top {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.job-title {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--brand-ink);
  line-height: 1.2;
}

.industry-tag {
  background: rgba(184, 107, 75, 0.1) !important;
  color: var(--brand-mocha-deep) !important;
}

:global(.dark) .industry-tag {
  background: rgba(232, 180, 150, 0.12) !important;
  color: #e8b496 !important;
}

.job-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 13px;
  color: var(--brand-ink-soft);
  margin-bottom: 12px;
}

.job-meta .dot {
  opacity: 0.5;
}

.salary {
  font-family: var(--serif-display);
  font-style: italic;
  color: var(--brand-mocha-deep);
  font-weight: 500;
}

:global(.dark) .salary {
  color: #e8b496;
}

.job-desc {
  font-size: 15px;
  line-height: 1.7;
  color: var(--brand-ink);
  opacity: 0.85;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 14px;
  max-width: 720px;
}

.kw-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.kw-chip {
  font-size: 11.5px;
  padding: 3px 12px;
  border-radius: 999px;
  border: 1px solid var(--brand-line);
  color: var(--brand-ink-soft);
  letter-spacing: 0.04em;
  background: transparent;
}

/* Actions */
.job-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
  align-self: center;
}

.row-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border-radius: 999px;
  border: 1px solid var(--brand-line);
  background: transparent;
  font-size: 12.5px;
  cursor: pointer;
  color: var(--brand-ink);
  transition: all 0.25s ease;
  white-space: nowrap;
}

.row-btn:hover {
  border-color: var(--brand-mocha);
  color: var(--brand-mocha-deep);
}

.row-btn .arrow {
  transition: transform 0.25s ease;
}
.row-btn:hover .arrow {
  transform: translateX(3px);
}

.row-btn.ghost {
  /* 默认样式即可 */
}
.row-btn.danger {
  color: #c44536;
}
.row-btn.danger:hover {
  border-color: #c44536;
  color: #c44536;
  background: rgba(196, 69, 54, 0.06);
}

/* Empty */
.empty-block {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 72px 16px;
}
.empty-no {
  font-size: 88px;
  color: var(--brand-mocha);
  opacity: 0.4;
  line-height: 1;
  margin-bottom: 12px;
}
.empty-headline {
  font-size: 28px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--brand-ink);
}
.empty-sub {
  font-family: var(--serif-display);
  font-style: italic;
  color: var(--brand-ink-soft);
  margin-bottom: 24px;
}

/* Pager */
.pager {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: center;
  margin-top: 40px;
  padding-top: 28px;
  border-top: 1px solid var(--brand-line);
}

@media (max-width: 768px) {
  .editorial-jobs {
    padding: 24px 20px;
  }
  .job-row {
    grid-template-columns: 40px 1fr;
  }
  .job-actions {
    grid-column: 2 / 3;
    flex-direction: row;
    flex-wrap: wrap;
    align-items: flex-start;
    margin-top: 4px;
  }
}
</style>

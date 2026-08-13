<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { $t } from '@/locales';
import { fetchGetMyMatchPage, type MatchApi } from '@/service/api/match';

defineOptions({ name: 'matches' });

const router = useRouter();

const matchList = ref<MatchApi.JobStudentMatchListItem[]>([]);
const loading = ref(false);
const pagination = reactive({
  page: 1,
  limit: 20,
  total: 0
});

const hasMatches = computed(() => matchList.value.length > 0);

async function loadData() {
  loading.value = true;
  try {
    const { data, error } = await fetchGetMyMatchPage({
      page: pagination.page,
      limit: pagination.limit
    });
    if (!error && data) {
      matchList.value = data.list || [];
      pagination.total = data.total || 0;
    }
  } catch {
    window.$message?.error($t('common.requestFailed'));
  } finally {
    loading.value = false;
  }
}

function handlePageChange(page: number) {
  pagination.page = page;
  loadData();
}

function handleViewDetail(jobId?: number) {
  if (jobId == null) return;
  router.push({ name: 'job-detail', query: { id: String(jobId) } });
}

function handleGoJobs() {
  router.push({ name: 'jobs' });
}

function formatScore(score?: number) {
  return score == null ? '-' : String(score);
}

function formatSkillRate(rate?: number) {
  if (rate == null) return '-';
  return `${Math.round(rate * 100)}%`;
}

function formatUpdatedAt(value?: string) {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 19);
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <NSpin :show="loading">
    <div class="h-full min-h-[500px] bg-slate-50/50 p-6 dark:bg-dark-100">
      <div class="mb-6 flex items-center justify-between">
        <h1 class="text-2xl font-bold text-slate-800 dark:text-gray-200">
          {{ $t('page.matches.title') }}
        </h1>
        <NButton @click="handleGoJobs">{{ $t('page.matches.goJobs') }}</NButton>
      </div>

      <div v-if="hasMatches" class="space-y-4">
        <NCard
          v-for="item in matchList"
          :key="item.id"
          hoverable
          class="cursor-pointer rounded-xl border-slate-200 transition-all hover:shadow-md dark:border-gray-700"
          @click="handleViewDetail(item.jobId)"
        >
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0 flex-1">
              <div class="mb-2 flex flex-wrap items-center gap-3">
                <h3 class="text-xl font-bold text-slate-800 dark:text-gray-200">
                  {{ item.jobName || '-' }}
                </h3>
                <NTag type="success" size="small">
                  {{ $t('page.matches.overallScore') }} {{ formatScore(item.overallScore) }}
                </NTag>
                <NTag type="info" size="small">
                  {{ $t('page.matches.keySkillRate') }} {{ formatSkillRate(item.keySkillMatchRate) }}
                </NTag>
              </div>
              <div class="mb-2 text-sm text-slate-600 dark:text-gray-400">
                {{ item.companyName || '-' }}
              </div>
              <p class="line-clamp-2 text-sm text-slate-700 dark:text-gray-300">
                {{ item.summary || $t('page.matches.noSummary') }}
              </p>
            </div>
            <div class="shrink-0 text-right text-xs text-slate-500 dark:text-gray-500">
              <div>{{ $t('page.matches.updatedAt') }}</div>
              <div class="mt-1">{{ formatUpdatedAt(item.updatedAt) }}</div>
            </div>
          </div>
        </NCard>

        <div class="flex justify-center pt-2">
          <NPagination
            :page="pagination.page"
            :page-size="pagination.limit"
            :item-count="pagination.total"
            @update:page="handlePageChange"
          />
        </div>
      </div>

      <NEmpty v-else :description="$t('page.matches.empty')">
        <template #extra>
          <NButton type="primary" @click="handleGoJobs">{{ $t('page.matches.goJobs') }}</NButton>
        </template>
      </NEmpty>
    </div>
  </NSpin>
</template>

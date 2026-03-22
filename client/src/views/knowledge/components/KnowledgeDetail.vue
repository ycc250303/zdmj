<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { fetchGetKnowledgeDetail, fetchVectorTaskStatus } from '@/service/api/knowledge';
import type { KnowledgeApi } from '@/service/api/knowledge';
import { fetchGetProjectList } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import VectorStatusTag from './VectorStatusTag.vue';

interface Props {
  knowledgeId: number;
  show: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits(['update:show']);

const loading = ref(true);
const refreshing = ref(false);
const detail = ref<KnowledgeApi.KnowledgeDTO | null>(null);
const projectList = ref<ResumeApi.ProjectDTO[]>([]);
const pollTimer = ref<NodeJS.Timeout | null>(null);

const knowledgeTypeLabels: Record<number, { label: string; type: 'primary' | 'info' | 'warning' }> = {
  1: { label: '项目文档', type: 'primary' },
  2: { label: 'GitHub 代码', type: 'info' },
  3: { label: 'DeepWiki', type: 'warning' }
};

const showModal = computed({
  get: () => props.show,
  set: (val) => emit('update:show', val)
});

function parseTag(tag: string[] | string): string[] {
  if (typeof tag === 'string') {
    try {
      return JSON.parse(tag);
    } catch {
      return [];
    }
  }
  return tag || [];
}

async function loadProjects() {
  try {
    const { data, error } = await fetchGetProjectList();
    if (!error && data) {
      projectList.value = data;
    }
  } catch (err) {
    console.error('加载项目列表失败:', err);
  }
}

function getProjectName(projectId: number): string {
  const project = projectList.value.find(p => p.id === projectId);
  return project?.name || `项目 ${projectId}`;
}

async function loadDetail() {
  if (!props.knowledgeId) return;

  loading.value = true;
  try {
    const { data, error } = await fetchGetKnowledgeDetail(props.knowledgeId);
    if (!error && data) {
      detail.value = {
        ...data,
        tag: parseTag(data.tag as unknown as string)
      };

      // 如果有待执行或执行中的任务，开始轮询
      if (data.vectorTaskId && (data.vectorTaskStatus === 1 || data.vectorTaskStatus === 2)) {
        startPolling();
      }
    }
  } finally {
    loading.value = false;
  }
}

function startPolling() {
  // 清除之前的定时器
  if (pollTimer.value) {
    clearInterval(pollTimer.value);
  }

  // 每3秒刷新一次状态
  pollTimer.value = setInterval(async () => {
    if (!props.knowledgeId) {
      stopPolling();
      return;
    }

    try {
      // 使用Java后端的刷新状态接口
      const { data, error } = await fetchVectorTaskStatus(props.knowledgeId);

      if (!error && data && detail.value) {
        // 将字符串状态转换为数字类型
        const statusMap: Record<string, KnowledgeApi.TaskStatus> = {
          'PENDING': 1,
          'RUNNING': 2,
          'SUCCESS': 3,
          'FAILED': 4,
          'CANCELLED': 5
        };

        const newStatus = statusMap[data.status] || 1;

        // 更新状态
        detail.value.vectorTaskStatus = newStatus;
        if (data.vectorIds && data.vectorIds.length > 0) {
          detail.value.vectorIds = data.vectorIds.map(String);
        }

        // 如果任务完成或失败，停止轮询
        if (newStatus === 3 || newStatus === 4 || newStatus === 5) {
          stopPolling();
          const message = newStatus === 3 ? '向量化任务完成！' : `向量化任务${data.status}`;
          window.$message?.success(message);
        }
      }
    } catch (err) {
      console.error('轮询任务状态失败:', err);
    }
  }, 3000);
}

function stopPolling() {
  if (pollTimer.value) {
    clearInterval(pollTimer.value);
    pollTimer.value = null;
  }
}

async function handleRefreshStatus() {
  if (!props.knowledgeId) {
    window.$message?.warning('没有可刷新的任务');
    return;
  }

  refreshing.value = true;
  try {
    const { data, error } = await fetchVectorTaskStatus(props.knowledgeId);
    console.log('查询任务状态返回:', { data, error });

    if (!error && data) {
      // 将字符串状态转换为数字类型
      const statusMap: Record<string, KnowledgeApi.TaskStatus> = {
        'PENDING': 1,
        'RUNNING': 2,
        'SUCCESS': 3,
        'FAILED': 4,
        'CANCELLED': 5
      };

      // 更新详情中的任务状态
      if (detail.value) {
        detail.value.vectorTaskStatus = statusMap[data.status] || 1;
        // 如果任务成功，更新向量ID（将number[]转换为string[]）
        if (data.vectorIds && data.vectorIds.length > 0) {
          detail.value.vectorIds = data.vectorIds.map(String);
        }
      }
      window.$message?.success(`状态刷新成功: ${data.status}`);
    }
  } finally {
    refreshing.value = false;
  }
}

function isGitHubUrl(url: string): boolean {
  return url.includes('github.com');
}

function isPdfUrl(url: string): boolean {
  return url.toLowerCase().includes('.pdf');
}

function handleDownloadContent() {
  if (!detail.value?.content) {
    window.$message?.warning('暂无内容链接');
    return;
  }
  // 直接打开链接
  window.open(detail.value.content, '_blank');
}

const buttonText = computed(() => {
  if (!detail.value?.content) return '打开文件';
  if (isGitHubUrl(detail.value.content)) return '查看仓库';
  if (isPdfUrl(detail.value.content)) return '下载文件';
  return '打开文件';
});

const buttonIcon = computed(() => {
  if (!detail.value?.content) return 'i-mdi-open-in-new';
  if (isGitHubUrl(detail.value.content)) return 'i-mdi-github';
  if (isPdfUrl(detail.value.content)) return 'i-mdi-download';
  return 'i-mdi-open-in-new';
});

watch(
  () => props.show,
  (newVal) => {
    if (newVal) {
      loadProjects();
      loadDetail();
    } else {
      // 模态框关闭时，停止轮询
      stopPolling();
    }
  },
  { immediate: true }
);
</script>

<template>
  <NModal
    v-model:show="showModal"
    preset="card"
    title="知识库详情"
    :style="{ width: '800px', maxHeight: '80vh' }"
    :segmented="{ content: 'soft' }"
  >
    <NSpin :show="loading">
      <div v-if="detail" class="space-y-6">
        <!-- 基本信息 -->
        <div>
          <h3 class="text-lg font-bold text-gray-800 mb-4 flex items-center gap-2">
            <div class="i-mdi-information-outline text-blue-500"></div>
            基本信息
          </h3>
          <NDescriptions :column="1" bordered label-placement="left" label-style="width: 120px">
            <NDescriptionsItem label="知识库名称">
              {{ detail.name }}
            </NDescriptionsItem>
            <NDescriptionsItem label="关联项目">
              {{ getProjectName(detail.projectId) }}
            </NDescriptionsItem>
            <NDescriptionsItem label="知识类型">
              <NTag :type="knowledgeTypeLabels[detail.type]?.type" size="small">
                {{ knowledgeTypeLabels[detail.type]?.label || '未知' }}
              </NTag>
            </NDescriptionsItem>
            <NDescriptionsItem label="内容链接">
              <a :href="detail.content" target="_blank" class="text-blue-500 hover:underline truncate block max-w-md">
                {{ detail.content }}
              </a>
            </NDescriptionsItem>
            <NDescriptionsItem label="标签">
              <div class="flex gap-2 flex-wrap">
                <NTag v-if="detail.tag && detail.tag.length > 0" v-for="tag in detail.tag" :key="tag" type="primary" size="small">
                  {{ tag }}
                </NTag>
                <span v-else class="text-gray-400">无标签</span>
              </div>
            </NDescriptionsItem>
          </NDescriptions>
        </div>

        <!-- 向量化信息 -->
        <div>
          <h3 class="text-lg font-bold text-gray-800 mb-4 flex items-center gap-2">
            <div class="i-mdi-vector-square text-purple-500"></div>
            向量化信息
            <NButton
              v-if="detail.vectorTaskId"
              size="tiny"
              quaternary
              type="primary"
              :loading="refreshing"
              @click="handleRefreshStatus"
              class="ml-auto"
            >
              <template #icon>
                <div class="i-mdi-refresh"></div>
              </template>
              刷新状态
            </NButton>
          </h3>
          <NDescriptions :column="1" bordered label-placement="left" label-style="width: 120px">
            <NDescriptionsItem label="任务状态">
              <VectorStatusTag v-if="detail.vectorTaskStatus" :status="detail.vectorTaskStatus" />
              <span v-else class="text-gray-400">无任务</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="任务ID">
              <code v-if="detail.vectorTaskId" class="bg-gray-100 px-2 py-1 rounded text-sm">
                {{ detail.vectorTaskId }}
              </code>
              <span v-else class="text-gray-400">无任务</span>
            </NDescriptionsItem>
            <NDescriptionsItem label="向量数量">
              <NTag v-if="detail.vectorIds && detail.vectorIds.length > 0" type="success" size="small">
                {{ detail.vectorIds.length }} 个向量
              </NTag>
              <span v-else class="text-gray-400">0</span>
            </NDescriptionsItem>
          </NDescriptions>
        </div>

      </div>
      <NEmpty v-else description="暂无数据" />
    </NSpin>

    <template #footer>
      <div class="flex justify-end gap-3">
        <NButton type="primary" @click="handleDownloadContent">
          <template #icon>
            <div :class="buttonIcon"></div>
          </template>
          {{ buttonText }}
        </NButton>
        <NButton @click="showModal = false">关闭</NButton>
      </div>
    </template>
  </NModal>
</template>

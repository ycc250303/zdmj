<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { fetchGetKnowledgeDetail } from '@/service/api/knowledge';
import type { KnowledgeApi } from '@/service/api/knowledge';
import { fetchGetProjectList } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';

interface Props {
  knowledgeId: number;
  show: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits(['update:show']);

const loading = ref(true);
const detail = ref<KnowledgeApi.KnowledgeDTO | null>(null);
const projectList = ref<ResumeApi.ProjectDTO[]>([]);

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
    }
  } finally {
    loading.value = false;
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

// 向量化状态信息
const vectorInfo = computed(() => {
  if (!detail.value) return null;

  const hasVectors = detail.value.vectorIds && detail.value.vectorIds.length > 0;
  const hasTask = detail.value.vectorTaskId || detail.value.vectorTaskStatus;

  return {
    hasVectors,
    hasTask,
    vectorCount: hasVectors ? detail.value.vectorIds.length : 0,
    taskId: detail.value.vectorTaskId,
    status: detail.value.vectorTaskStatus
  };
});

watch(
  () => props.show,
  (newVal) => {
    if (newVal) {
      loadProjects();
      loadDetail();
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
        <div v-if="vectorInfo">
          <h3 class="text-lg font-bold text-gray-800 mb-4 flex items-center gap-2">
            <div class="i-mdi-vector-square text-purple-500"></div>
            向量化信息
          </h3>
          <NDescriptions :column="1" bordered label-placement="left" label-style="width: 120px">
            <NDescriptionsItem label="向量状态">
              <NTag v-if="vectorInfo.hasVectors" type="success" size="small">
                <template #icon>
                  <div class="i-mdi-check-circle"></div>
                </template>
                已向量化 ({{ vectorInfo.vectorCount }} 个向量)
              </NTag>
              <NTag v-else type="default" size="small">
                <template #icon>
                  <div class="i-mdi-information-outline"></div>
                </template>
                未向量化
              </NTag>
            </NDescriptionsItem>
            <NDescriptionsItem v-if="vectorInfo.taskId" label="任务ID">
              <code class="bg-gray-100 px-2 py-1 rounded text-sm text-gray-600">
                {{ vectorInfo.taskId }}
              </code>
            </NDescriptionsItem>
            <NDescriptionsItem v-if="vectorInfo.status" label="任务状态">
              <NTag :type="vectorInfo.status === 'SUCCESS' ? 'success' : 'default'" size="small">
                {{ vectorInfo.status }}
              </NTag>
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

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue';
import { fetchGetKnowledgeList, fetchDeleteKnowledge, fetchVectorTaskStatus, fetchGetKnowledgeDetail } from '@/service/api/knowledge';
import type { KnowledgeApi } from '@/service/api/knowledge';
import { fetchGetProjectList } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';
import VectorStatusTag from './components/VectorStatusTag.vue';
import KnowledgeForm from './components/KnowledgeForm.vue';
import KnowledgeDetail from './components/KnowledgeDetail.vue';

const isEditing = ref(false);
const knowledgeList = ref<KnowledgeApi.KnowledgeDTO[]>([]);
const currentEditData = ref<KnowledgeApi.KnowledgeUpdate | undefined>(undefined);
const loading = ref(true);
const projectList = ref<ResumeApi.ProjectDTO[]>([]);
const projectLoading = ref(false);

// 任务轮询管理
const pollingTasks = ref<Map<number, NodeJS.Timeout>>(new Map());
const POLLING_INTERVAL = 3000; // 3秒轮询一次

// 详情模态框
const showDetail = ref(false);
const currentDetailId = ref<number>(0);

const pagination = reactive({
  page: 1,
  limit: 10,
  total: 0
});

const searchParams = reactive({
  projectId: undefined as number | undefined,
  type: undefined as KnowledgeApi.KnowledgeType | undefined
});

const knowledgeTypeOptions = [
  { label: '全部', value: undefined },
  { label: '项目文档', value: 1 },
  { label: 'GitHub 代码', value: 2 },
  { label: 'DeepWiki', value: 3 }
];

const knowledgeTypeLabels: Record<number, { label: string; type: 'primary' | 'info' | 'success' | 'default' | 'warning' }> = {
  1: { label: '项目文档', type: 'primary' },
  2: { label: 'GitHub', type: 'info' },
  3: { label: 'DeepWiki', type: 'warning' }
};

async function loadProjects() {
  projectLoading.value = true;
  try {
    const { data, error } = await fetchGetProjectList();
    if (!error && data) {
      projectList.value = data;
    }
  } finally {
    projectLoading.value = false;
  }
}

function getProjectName(projectId: number): string {
  const project = projectList.value.find(p => p.id === projectId);
  return project?.name || `项目 ${projectId}`;
}

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

async function loadData() {
  loading.value = true;
  try {
    const { data, error } = await fetchGetKnowledgeList({
      page: pagination.page,
      limit: pagination.limit,
      projectId: searchParams.projectId,
      type: searchParams.type
    });

    console.log('API 返回数据:', { data, error });

    if (!error && data) {
      knowledgeList.value = (data.data || []).map(item => ({
        ...item,
        tag: parseTag(item.tag as unknown as string)
      }));
      pagination.total = data.total || 0;
      console.log('处理后的列表:', knowledgeList.value);
    }
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pagination.page = 1;
  loadData();
}

function handleReset() {
  searchParams.projectId = undefined;
  searchParams.type = undefined;
  pagination.page = 1;
  loadData();
}

function handleAddNew() {
  currentEditData.value = undefined;
  isEditing.value = true;
}

function handleEdit(item: KnowledgeApi.KnowledgeUpdate) {
  currentEditData.value = { ...item, tag: parseTag(item.tag as unknown as string) };
  isEditing.value = true;
}

async function handleDelete(id: number) {
  const { error } = await fetchDeleteKnowledge(id);
  if (!error) {
    window.$message?.success($t('page.profile.common.delete') + '成功');
    loadData();
  }
}

function handleViewDetail(id: number) {
  currentDetailId.value = id;
  showDetail.value = true;
}

function handlePageChange(page: number) {
  pagination.page = page;
  loadData();
}

// 开始轮询任务状态
function startPollingTaskStatus(knowledgeId: number) {
  // 如果已经在轮询，先清除
  stopPollingTaskStatus(knowledgeId);

  const poll = async () => {
    try {
      const { data, error } = await fetchVectorTaskStatus(knowledgeId);

      if (!error && data) {
        // 更新列表中对应知识库的任务状态
        const index = knowledgeList.value.findIndex(item => item.id === knowledgeId);
        if (index !== -1) {
          // 将字符串状态转换为数字
          const statusMap: Record<string, KnowledgeApi.TaskStatus> = {
            'PENDING': 1,
            'RUNNING': 2,
            'SUCCESS': 3,
            'FAILED': 4,
            'CANCELLED': 5
          };
          knowledgeList.value[index].vectorTaskStatus = statusMap[data.status] || 1;

          // 如果状态是终态（成功、失败、取消），停止轮询
          if (data.status === 'SUCCESS' || data.status === 'FAILED' || data.status === 'CANCELLED') {
            stopPollingTaskStatus(knowledgeId);

            // 显示提示
            if (data.status === 'SUCCESS') {
              window.$message?.success('知识库向量化完成');
            } else if (data.status === 'FAILED') {
              window.$message?.error(`知识库向量化失败: ${data.errorMessage || '未知错误'}`);
            }
          }
        }
      }
    } catch (err) {
      console.error('轮询任务状态失败:', err);
    }
  };

  // 立即执行一次
  poll();

  // 设置定时轮询
  const timerId = setInterval(poll, POLLING_INTERVAL);
  pollingTasks.value.set(knowledgeId, timerId);
}

// 停止轮询任务状态
function stopPollingTaskStatus(knowledgeId: number) {
  const timerId = pollingTasks.value.get(knowledgeId);
  if (timerId) {
    clearInterval(timerId);
    pollingTasks.value.delete(knowledgeId);
  }
}

// 清除所有轮询任务
function clearAllPollingTasks() {
  pollingTasks.value.forEach((timerId) => {
    clearInterval(timerId);
  });
  pollingTasks.value.clear();
}

function onFormSuccess(data: KnowledgeApi.KnowledgeDTO) {
  isEditing.value = false;
  loadData();

  // 如果创建/更新的是��知识库或有待执行/执行中的任务，开始轮询
  if (data && data.id) {
    // 刷新列表后，找到对应的知识库并检查其任务状态
    setTimeout(() => {
      const item = knowledgeList.value.find(k => k.id === data.id);
      if (item && (item.vectorTaskStatus === 1 || item.vectorTaskStatus === 2)) {
        startPollingTaskStatus(data.id);
      }
    }, 500);
  }
}

onMounted(() => {
  loadProjects();
  loadData();
});
</script>

<template>
  <div class="p-6 h-full overflow-y-auto bg-gray-50">
    <div v-if="!isEditing" class="max-w-5xl mx-auto">
      <!-- 标题栏 -->
      <div class="flex justify-between items-center mb-6">
        <h1 class="text-2xl font-bold text-gray-800">知识库管理</h1>
        <NButton type="primary" @click="handleAddNew">
          <template #icon>
            <div class="i-mdi-plus"></div>
          </template>
          添加知识
        </NButton>
      </div>

      <!-- 搜索栏 -->
      <div class="bg-white p-4 rounded-xl border border-gray-100 shadow-sm mb-6">
        <div class="flex gap-4 items-center flex-wrap">
          <NSelect
            v-model:value="searchParams.projectId"
            :options="[{ label: '全部项目', value: undefined }, ...projectList.map(p => ({ label: p.name, value: p.id }))]"
            placeholder="选择项目"
            clearable
            class="w-48"
            :loading="projectLoading"
          />
          <NSelect
            v-model:value="searchParams.type"
            :options="knowledgeTypeOptions"
            placeholder="知识类型"
            clearable
            class="w-40"
          />
          <NButton type="primary" @click="handleSearch">
            <template #icon>
              <div class="i-mdi-magnify"></div>
            </template>
            搜索
          </NButton>
          <NButton @click="handleReset">重置</NButton>
        </div>
      </div>

      <!-- 列表区域 -->
      <NSpin :show="loading">
        <div v-if="knowledgeList.length === 0" class="text-center py-20 text-gray-400 bg-white rounded-xl border border-gray-100 shadow-sm">
          <div class="i-mdi-book-open-page-variant-outline text-6xl mb-4 mx-auto opacity-50"></div>
          <p>{{ $t('page.profile.common.empty') }}</p>
        </div>

        <div v-else class="flex flex-col gap-4">
          <NCard v-for="item in knowledgeList" :key="item.id" hoverable class="rounded-lg shadow-sm border-gray-100">
            <div class="flex justify-between items-start">
              <div class="flex-1">
                <div class="flex items-center gap-3 mb-2">
                  <div class="i-mdi-book-outline text-2xl text-blue-500"></div>
                  <h3 class="text-lg font-bold text-gray-800">{{ item.name }}</h3>
                  <NTag :type="knowledgeTypeLabels[item.type]?.type" size="small">
                    {{ knowledgeTypeLabels[item.type]?.label || '未知' }}
                  </NTag>
                  <VectorStatusTag v-if="item.vectorTaskStatus" :status="item.vectorTaskStatus" />
                </div>
                <div class="text-gray-500 text-sm mb-2">
                  <span class="i-mdi-folder-outline mr-1"></span>
                  {{ getProjectName(item.projectId) }}
                </div>
                <div class="text-gray-400 text-xs mb-3 truncate max-w-lg">
                  <span class="i-mdi-link-variant mr-1"></span>
                  {{ item.content }}
                </div>
                <div v-if="item.tag && item.tag.length > 0" class="flex gap-2 flex-wrap">
                  <NTag v-for="tag in item.tag" :key="tag" type="primary" size="small" :bordered="false">
                    {{ tag }}
                  </NTag>
                </div>
              </div>
              <div class="flex gap-2 ml-4">
                <NButton size="small" tertiary type="info" @click="handleViewDetail(item.id)">
                  查看详情
                </NButton>
                <NButton size="small" secondary @click="handleEdit(item)">
                  {{ $t('page.profile.common.edit') }}
                </NButton>
                <NPopconfirm @positive-click="handleDelete(item.id)">
                  <template #trigger>
                    <NButton size="small" type="error" ghost>
                      {{ $t('page.profile.common.delete') }}
                    </NButton>
                  </template>
                  {{ $t('page.profile.common.confirmDelete') }}
                </NPopconfirm>
              </div>
            </div>
          </NCard>
        </div>

        <!-- 分页 -->
        <div v-if="pagination.total > pagination.limit" class="flex justify-end mt-6">
          <NPagination
            v-model:page="pagination.page"
            :page-size="pagination.limit"
            :item-count="pagination.total"
            @update:page="handlePageChange"
          />
        </div>
      </NSpin>
    </div>

    <!-- 编辑表单 -->
    <div v-else class="py-4">
      <KnowledgeForm :initial-data="currentEditData" @success="onFormSuccess" @cancel="isEditing = false" />
    </div>

    <!-- 详情模态框 -->
    <KnowledgeDetail :knowledge-id="currentDetailId" :show="showDetail" @update:show="showDetail = $event" />
  </div>
</template>

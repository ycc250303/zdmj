<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { fetchGetKnowledgeList, fetchDeleteKnowledge } from '@/service/api/knowledge';
import type { KnowledgeApi } from '@/service/api/knowledge';
import { fetchGetProjectList } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';
import KnowledgeForm from './components/KnowledgeForm.vue';
import KnowledgeDetail from './components/KnowledgeDetail.vue';

const isEditing = ref(false);
const knowledgeList = ref<KnowledgeApi.KnowledgeDTO[]>([]);
const currentEditData = ref<KnowledgeApi.KnowledgeUpdate | undefined>(undefined);
const loading = ref(true);
const projectList = ref<ResumeApi.ProjectDTO[]>([]);
const projectLoading = ref(false);

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

// 检查知识库的向量化状态
function getVectorStatus(item: KnowledgeApi.KnowledgeDTO) {
  const hasVectors = item.vectorIds && item.vectorIds.length > 0;
  if (hasVectors) {
    return {
      text: `已向量化 (${item.vectorIds.length})`,
      type: 'success' as const,
      icon: 'i-mdi-check-circle'
    };
  }
  return {
    text: '未向量化',
    type: 'default' as const,
    icon: 'i-mdi-information-outline'
  };
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

function onFormSuccess() {
  isEditing.value = false;
  loadData();
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
        <!-- 骨架屏加载 -->
        <template v-if="loading">
          <div v-for="i in 5" :key="i" class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm mb-4">
            <NSkeleton height="24px" width="60%" class="mb-4" />
            <NSkeleton height="16px" width="40%" class="mb-2" />
            <NSkeleton height="16px" width="80%" />
          </div>
        </template>

        <!-- 空状态 -->
        <div v-else-if="knowledgeList.length === 0" class="text-center py-20 bg-white rounded-xl border border-gray-100 shadow-sm">
          <div class="i-mdi-book-open-page-variant-outline text-6xl mb-4 mx-auto text-gray-300"></div>
          <p class="text-gray-500 mb-6">{{ $t('page.profile.common.empty') }}</p>
          <NButton type="primary" @click="handleAddNew">
            <template #icon>
              <div class="i-mdi-plus"></div>
            </template>
            创建第一个知识库
          </NButton>
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
                  <NTag :type="getVectorStatus(item).type" size="small">
                    <template #icon>
                      <div :class="getVectorStatus(item).icon"></div>
                    </template>
                    {{ getVectorStatus(item).text }}
                  </NTag>
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

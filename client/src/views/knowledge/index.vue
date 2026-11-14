<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { fetchDeleteKnowledgeDocument, fetchGetKnowledgeDocumentList } from '@/service/api/knowledge';
import type { KnowledgeApi } from '@/service/api/knowledge';
import { $t } from '@/locales';
import KnowledgeForm from './components/KnowledgeForm.vue';
import KnowledgeDetail from './components/KnowledgeDetail.vue';

const isEditing = ref(false);
const knowledgeList = ref<KnowledgeApi.KnowledgeDocumentDTO[]>([]);
const currentEditData = ref<KnowledgeApi.KnowledgeDocumentUpdate | undefined>(undefined);
const loading = ref(true);

// 详情模态框
const showDetail = ref(false);
const currentDetailId = ref<number>(0);

const pagination = reactive({
  page: 1,
  limit: 20,
  total: 0
});

const searchParams = reactive({
  type: undefined as KnowledgeApi.KnowledgeType | undefined
});

const knowledgeTypeOptions = computed(() => [
  { label: $t('page.knowledge.typeAll'), value: undefined },
  { label: $t('page.knowledge.typeProjectDoc'), value: 1 }
  // type=2 GitHub 暂不支持
  // type=3 DeepWiki 暂不支持
]);

const knowledgeTypeLabels = computed<
  Record<number, { label: string; type: 'primary' | 'info' | 'success' | 'default' | 'warning' }>
>(() => ({
  1: { label: $t('page.knowledge.typeProjectDoc'), type: 'primary' },
  2: { label: 'GitHub', type: 'info' },
  3: { label: 'DeepWiki', type: 'warning' }
}));

// 向量化状态映射
const embeddingStatusLabels = computed<
  Record<string, { text: string; type: 'success' | 'warning' | 'error' | 'default'; icon: string }>
>(() => ({
  PENDING: { text: $t('page.knowledge.embeddingPending'), type: 'default', icon: '🕐' },
  RUNNING: { text: $t('page.knowledge.embeddingRunning'), type: 'warning', icon: '⏳' },
  SUCCESS: { text: $t('page.knowledge.embeddingSuccess'), type: 'success', icon: '✅' },
  FAILED: { text: $t('page.knowledge.embeddingFailed'), type: 'error', icon: '⚠️' }
}));

// 获取向量化状态显示
function getEmbeddingStatus(item: KnowledgeApi.KnowledgeDocumentDTO) {
  if (item.fileStatus === 0) return { text: '正在保存', type: 'warning' as const, icon: '⏳' };
  if (item.fileStatus === 2) return { text: '文件保存失败', type: 'error' as const, icon: '⚠️' };
  if (item.fileStatus === 3) return { text: '删除尚未完成', type: 'warning' as const, icon: '⏳' };
  const status = item.embeddingStatus || 'PENDING';
  return embeddingStatusLabels.value[status] || embeddingStatusLabels.value.PENDING;
}

async function loadData() {
  loading.value = true;
  try {
    const { data, error } = await fetchGetKnowledgeDocumentList({
      page: pagination.page,
      limit: pagination.limit
    });

    console.log('API 返回数据:', { data, error });

    if (!error && data) {
      // 后端分页结构：list, total, page, limit
      knowledgeList.value = (data.list || []).filter(item => {
        // 如果选择了类型筛选，应用筛选
        if (searchParams.type !== undefined && item.type !== searchParams.type) {
          return false;
        }
        return true;
      });
      pagination.total = data.total || 0;
      pagination.page = data.page || 1;
      pagination.limit = data.limit || 20;
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
  searchParams.type = undefined;
  pagination.page = 1;
  loadData();
}

function handleAddNew() {
  currentEditData.value = undefined;
  isEditing.value = true;
}

function handleEdit(item: KnowledgeApi.KnowledgeDocumentUpdate) {
  if (item.type === 2) {
    window.$message?.warning($t('page.knowledge.typeGithubUnsupported'));
    return;
  }
  currentEditData.value = { ...item };
  isEditing.value = true;
}

async function handleDelete(id: number) {
  const { error } = await fetchDeleteKnowledgeDocument(id);
  if (!error) {
    window.$message?.success($t('page.knowledge.deleteSuccess'));
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
  loadData();
});
</script>

<template>
  <div class="h-full overflow-y-auto bg-gray-50 p-6 dark:bg-dark-100">
    <div v-if="!isEditing" class="mx-auto max-w-5xl">
      <!-- 标题栏 -->
      <div class="mb-6 flex items-center justify-between">
        <h1 class="text-2xl text-gray-800 font-bold dark:text-gray-200">{{ $t('page.knowledge.title') }}</h1>
        <NButton type="primary" @click="handleAddNew">
          <template #icon>
            <span>+</span>
          </template>
          {{ $t('page.knowledge.addBtn') }}
        </NButton>
      </div>

      <!-- 搜索栏 -->
      <div class="mb-6 border border-gray-100 rounded-xl bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-dark-200">
        <div class="flex flex-wrap items-center gap-4">
          <NSelect
            v-model:value="searchParams.type"
            :options="knowledgeTypeOptions"
            :placeholder="$t('page.knowledge.type')"
            clearable
            class="w-40"
          />
          <NButton type="primary" @click="handleSearch">
            <template #icon>
              <span>🔍</span>
            </template>
            {{ $t('common.search') }}
          </NButton>
          <NButton @click="handleReset">{{ $t('common.reset') }}</NButton>
        </div>
      </div>

      <!-- 列表区域 -->
      <NSpin :show="loading">
        <!-- 骨架屏加载 -->
        <template v-if="loading">
          <div
            v-for="i in 5"
            :key="i"
            class="mb-4 border border-gray-100 rounded-xl bg-white p-6 shadow-sm dark:border-gray-700 dark:bg-dark-200"
          >
            <NSkeleton height="24px" width="60%" class="mb-4" />
            <NSkeleton height="16px" width="40%" class="mb-2" />
            <NSkeleton height="16px" width="80%" />
          </div>
        </template>

        <!-- 空状态 -->
        <div
          v-else-if="knowledgeList.length === 0"
          class="border border-gray-100 rounded-xl bg-white py-20 text-center shadow-sm dark:border-gray-700 dark:bg-dark-200"
        >
          <span class="mx-auto mb-4 text-6xl text-gray-300">📖</span>
          <p class="mb-6 text-gray-500 dark:text-gray-400">{{ $t('page.profile.common.empty') }}</p>
          <NButton type="primary" @click="handleAddNew">
            <template #icon>
              <span>+</span>
            </template>
            {{ $t('page.knowledge.createFirst') }}
          </NButton>
        </div>

        <div v-else class="flex flex-col gap-4">
          <NCard
            v-for="item in knowledgeList"
            :key="item.id"
            hoverable
            class="border-gray-100 rounded-lg shadow-sm dark:border-gray-700"
          >
            <div class="flex items-start justify-between">
              <div class="flex-1">
                <div class="mb-2 flex items-center gap-3">
                  <span class="text-2xl text-blue-500">📘</span>
                  <h3 class="text-lg text-gray-800 font-bold dark:text-gray-200">{{ item.title }}</h3>
                  <NTag :type="knowledgeTypeLabels[item.type]?.type" size="small">
                    {{ knowledgeTypeLabels[item.type]?.label || $t('page.knowledge.unknown') }}
                  </NTag>
                  <NTag :type="getEmbeddingStatus(item).type" size="small">
                    <template #icon>
                      <div :class="getEmbeddingStatus(item).icon"></div>
                    </template>
                    {{ getEmbeddingStatus(item).text }}
                  </NTag>
                </div>
                <div class="mb-3 max-w-lg truncate text-xs text-gray-400 dark:text-gray-500">
                  <span class="mr-1">🔗</span>
                  {{ item.metadata?.fileName || item.title }}
                </div>
                <div v-if="item.lastError" class="mt-2 text-xs text-red-500">
                  <span class="mr-1">⚠️</span>
                  {{ item.lastError }}
                </div>
              </div>
              <div class="ml-4 flex gap-2">
                <NButton size="small" tertiary type="info" @click="handleViewDetail(item.id!)">
                  {{ $t('page.knowledge.viewDetail') }}
                </NButton>
                <NButton
                  v-if="item.type !== 2"
                  size="small"
                  secondary
                  @click="handleEdit(item as KnowledgeApi.KnowledgeDocumentUpdate)"
                >
                  {{ $t('page.profile.common.edit') }}
                </NButton>
                <NPopconfirm @positive-click="handleDelete(item.id!)">
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
        <div v-if="pagination.total > pagination.limit" class="mt-6 flex justify-end">
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
      <KnowledgeForm
        :initial-data="currentEditData"
        @success="onFormSuccess"
        @refresh="loadData"
        @cancel="isEditing = false"
      />
    </div>

    <!-- 详情模态框 -->
    <KnowledgeDetail
      :knowledge-id="currentDetailId"
      :show="showDetail"
      @refresh="loadData"
      @update:show="showDetail = $event"
    />
  </div>
</template>

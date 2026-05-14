<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { fetchGetKnowledgeDocumentList, fetchDeleteKnowledgeDocument } from '@/service/api/knowledge';
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

const knowledgeTypeOptions = [
  { label: '全部', value: undefined },
  { label: '项目文档', value: 1 },
  { label: 'GitHub 代码', value: 2 }
  // type=3 DeepWiki 暂不支持
];

const knowledgeTypeLabels: Record<number, { label: string; type: 'primary' | 'info' | 'success' | 'default' | 'warning' }> = {
  1: { label: '项目文档', type: 'primary' },
  2: { label: 'GitHub', type: 'info' },
  3: { label: 'DeepWiki', type: 'warning' }
};

// 向量化状态映射
const embeddingStatusLabels: Record<string, { text: string; type: 'success' | 'warning' | 'error' | 'default'; icon: string }> = {
  PENDING: { text: '等待中', type: 'default', icon: '🕐' },
  RUNNING: { text: '向量化中...', type: 'warning', icon: '⏳' },
  SUCCESS: { text: '已向量化', type: 'success', icon: '✅' },
  FAILED: { text: '向量化失败', type: 'error', icon: '⚠️' }
};

// 获取向量化状态显示
function getEmbeddingStatus(item: KnowledgeApi.KnowledgeDocumentDTO) {
  const status = item.embeddingStatus || 'PENDING';
  return embeddingStatusLabels[status] || embeddingStatusLabels.PENDING;
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
  currentEditData.value = { ...item };
  isEditing.value = true;
}

async function handleDelete(id: number) {
  const { error } = await fetchDeleteKnowledgeDocument(id);
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
  loadData();
});
</script>

<template>
  <div class="p-6 h-full overflow-y-auto bg-gray-50">
    <div v-if="!isEditing" class="max-w-5xl mx-auto">
      <!-- 标题栏 -->
      <div class="flex justify-between items-center mb-6">
        <h1 class="text-2xl font-bold text-gray-800">{{ $t('page.knowledge.title') }}</h1>
        <NButton type="primary" @click="handleAddNew">
          <template #icon>
            <span>+</span>
          </template>
          {{ $t('page.knowledge.addBtn') }}
        </NButton>
      </div>

      <!-- 搜索栏 -->
      <div class="bg-white p-4 rounded-xl border border-gray-100 shadow-sm mb-6">
        <div class="flex gap-4 items-center flex-wrap">
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
          <div v-for="i in 5" :key="i" class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm mb-4">
            <NSkeleton height="24px" width="60%" class="mb-4" />
            <NSkeleton height="16px" width="40%" class="mb-2" />
            <NSkeleton height="16px" width="80%" />
          </div>
        </template>

        <!-- 空状态 -->
        <div v-else-if="knowledgeList.length === 0" class="text-center py-20 bg-white rounded-xl border border-gray-100 shadow-sm">
          <span class="text-6xl mb-4 mx-auto text-gray-300">📖</span>
          <p class="text-gray-500 mb-6">{{ $t('page.profile.common.empty') }}</p>
          <NButton type="primary" @click="handleAddNew">
            <template #icon>
              <span>+</span>
            </template>
            {{ $t('page.knowledge.createFirst') }}
          </NButton>
        </div>

        <div v-else class="flex flex-col gap-4">
          <NCard v-for="item in knowledgeList" :key="item.id" hoverable class="rounded-lg shadow-sm border-gray-100">
            <div class="flex justify-between items-start">
              <div class="flex-1">
                <div class="flex items-center gap-3 mb-2">
                  <span class="text-2xl text-blue-500">📘</span>
                  <h3 class="text-lg font-bold text-gray-800">{{ item.title }}</h3>
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
                <div class="text-gray-400 text-xs mb-3 truncate max-w-lg">
                  <span class="mr-1">🔗</span>
                  {{ item.content }}
                </div>
                <div v-if="item.lastError" class="text-red-500 text-xs mt-2">
                  <span class="mr-1">⚠️</span>
                  {{ item.lastError }}
                </div>
              </div>
              <div class="flex gap-2 ml-4">
                <NButton size="small" tertiary type="info" @click="handleViewDetail(item.id!)">
                  {{ $t('page.knowledge.viewDetail') }}
                </NButton>
                <NButton size="small" secondary @click="handleEdit(item as KnowledgeApi.KnowledgeDocumentUpdate)">
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

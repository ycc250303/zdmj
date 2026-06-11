<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
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

const knowledgeTypeOptions = computed(() => [
  { label: $t('page.knowledge.typeAll'), value: undefined },
  { label: $t('page.knowledge.typeProjectDoc'), value: 1 },
  { label: $t('page.knowledge.typeGithub'), value: 2 }
  // type=3 DeepWiki 暂不支持
]);

const knowledgeTypeLabels = computed<Record<number, { label: string; type: 'primary' | 'info' | 'success' | 'default' | 'warning' }>>(() => ({
  1: { label: $t('page.knowledge.typeProjectDoc'), type: 'primary' },
  2: { label: 'GitHub', type: 'info' },
  3: { label: 'DeepWiki', type: 'warning' }
}));

// 向量化状态映射
const embeddingStatusLabels = computed<Record<string, { text: string; type: 'success' | 'warning' | 'error' | 'default'; icon: string }>>(() => ({
  PENDING: { text: $t('page.knowledge.embeddingPending'), type: 'default', icon: '🕐' },
  RUNNING: { text: $t('page.knowledge.embeddingRunning'), type: 'warning', icon: '⏳' },
  SUCCESS: { text: $t('page.knowledge.embeddingSuccess'), type: 'success', icon: '✅' },
  FAILED: { text: $t('page.knowledge.embeddingFailed'), type: 'error', icon: '⚠️' }
}));

// 获取向量化状态显示
function getEmbeddingStatus(item: KnowledgeApi.KnowledgeDocumentDTO) {
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
  <div class="editorial-page">
    <div v-if="!isEditing" class="editorial-wrap">
      <header class="section-head">
        <div class="head-meta"><span class="head-bar"></span><span class="head-tag">— KNOWLEDGE BASE —</span></div>
        <div class="head-row">
          <h1 class="head-title font-display">{{ $t('page.knowledge.title') }}</h1>
          <button class="primary-btn" @click="handleAddNew"><span class="plus">+</span>{{ $t('page.knowledge.addBtn') }}</button>
        </div>
      </header>
      <div class="head-rule"></div>

      <!-- 搜索 -->
      <div class="search-bar">
        <NSelect v-model:value="searchParams.type" :options="knowledgeTypeOptions" :placeholder="$t('page.knowledge.type')" clearable class="w-40" />
        <NButton @click="handleSearch">{{ $t('common.search') }}</NButton>
        <NButton @click="handleReset">{{ $t('common.reset') }}</NButton>
      </div>

      <NSpin :show="loading">
        <template v-if="loading">
          <div v-for="i in 5" :key="i" class="skeleton-row"><NSkeleton height="24px" width="60%" class="mb-3" /><NSkeleton height="14px" width="40%" /></div>
        </template>
        <div v-else-if="knowledgeList.length === 0" class="empty-block">
          <div class="empty-no font-display">∅</div>
          <p class="empty-headline font-display">No documents yet.</p>
          <p class="empty-sub">{{ $t('page.profile.common.empty') }}</p>
          <button class="primary-btn" @click="handleAddNew"><span class="plus">+</span>{{ $t('page.knowledge.createFirst') }}</button>
        </div>
        <div v-else class="items-list">
          <article v-for="(item, idx) in knowledgeList" :key="item.id" class="list-row">
            <div class="row-no font-display">{{ String(idx + 1 + (pagination.page - 1) * pagination.limit).padStart(2, '0') }}</div>
            <div class="row-content">
              <div class="row-top">
                <h3 class="row-title">{{ item.title }}</h3>
                <NTag :type="knowledgeTypeLabels[item.type]?.type" size="small">{{ knowledgeTypeLabels[item.type]?.label || $t('page.knowledge.unknown') }}</NTag>
                <NTag :type="getEmbeddingStatus(item).type" size="small">{{ getEmbeddingStatus(item).text }}</NTag>
              </div>
              <p class="row-desc">{{ item.content }}</p>
              <p v-if="item.lastError" class="row-error">{{ item.lastError }}</p>
            </div>
            <div class="row-actions">
              <button class="row-btn" @click="handleViewDetail(item.id!)">{{ $t('page.knowledge.viewDetail') }}<span class="arrow">→</span></button>
              <button class="row-btn" @click="handleEdit(item as KnowledgeApi.KnowledgeDocumentUpdate)">{{ $t('page.profile.common.edit') }}<span class="arrow">→</span></button>
              <NPopconfirm @positive-click="handleDelete(item.id!)"><template #trigger><button class="row-btn danger">{{ $t('page.profile.common.delete') }}</button></template>{{ $t('page.profile.common.confirmDelete') }}</NPopconfirm>
            </div>
          </article>
        </div>
        <div v-if="pagination.total > pagination.limit" class="pager-row"><NPagination v-model:page="pagination.page" :page-size="pagination.limit" :item-count="pagination.total" @update:page="handlePageChange" /></div>
      </NSpin>
    </div>
    <div v-else class="editorial-wrap py-4"><KnowledgeForm :initial-data="currentEditData" @success="onFormSuccess" @cancel="isEditing = false" /></div>
    <KnowledgeDetail :knowledge-id="currentDetailId" :show="showDetail" @update:show="showDetail = $event" />
  </div>
</template>

<style scoped>
.editorial-page { min-height: 100%; background: #fefefe; padding: 40px 56px 56px; overflow: auto; }
.editorial-wrap { max-width: 960px; }
.section-head { margin-bottom: 16px; }
.head-meta { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.head-bar { width: 40px; height: 2px; background: #c4a46c; }
.head-tag { font-size: 11px; letter-spacing: 0.22em; color: #6a6a6a; }
.head-row { display: flex; justify-content: space-between; align-items: center; gap: 24px; }
.head-title { font-size: clamp(32px, 4vw, 48px); font-weight: 600; letter-spacing: -0.02em; color: #1a1a1a; }
.head-rule { height: 1px; background: #e0e0e0; margin-bottom: 28px; }
.head-rule::after { content: ''; display: block; height: 1px; background: #e0e0e0; margin-top: 4px; }
.primary-btn { display: inline-flex; align-items: center; gap: 8px; padding: 10px 20px; border-radius: 999px; background: #c4a46c; color: #fff; border: none; font-size: 14px; font-weight: 500; cursor: pointer; transition: all 0.25s ease; }
.primary-btn:hover { background: #a08050; transform: translateY(-1px); }
.plus { font-size: 18px; }
.search-bar { display: flex; gap: 10px; align-items: center; margin-bottom: 32px; flex-wrap: wrap; }
.empty-block { text-align: center; padding: 80px 24px; }
.empty-no { font-size: 72px; color: #c4a46c; opacity: 0.3; }
.empty-headline { font-size: 24px; color: #1a1a1a; margin: 16px 0 8px; }
.empty-sub { color: #6a6a6a; margin-bottom: 20px; }
.skeleton-row { background: #fff; padding: 20px 24px; border-bottom: 1px solid #ebebeb; }
.items-list { display: flex; flex-direction: column; }
.list-row { display: grid; grid-template-columns: 48px 1fr auto; gap: 20px; align-items: flex-start; padding: 18px 0; border-bottom: 1px solid #ebebeb; transition: background 0.25s; }
.list-row:hover { background: rgba(196,164,108,0.03); }
.list-row:first-child { border-top: 1px solid #ebebeb; }
.row-no { font-size: 18px; color: #c4a46c; font-style: italic; padding-top: 2px; }
.row-top { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.row-title { font-size: 16px; font-weight: 600; color: #1a1a1a; }
.row-desc { font-size: 13px; color: #888; margin-top: 6px; line-height: 1.5; max-width: 500px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row-error { font-size: 12px; color: #c44536; margin-top: 4px; }
.row-actions { display: flex; gap: 6px; align-self: center; flex-wrap: wrap; }
.row-btn { display: inline-flex; align-items: center; gap: 6px; padding: 6px 16px; border-radius: 999px; border: 1px solid #ddd; background: #fff; color: #333; font-size: 13px; cursor: pointer; transition: all 0.22s ease; }
.row-btn:hover { border-color: #c4a46c; color: #c4a46c; }
.row-btn .arrow { transition: transform 0.22s; }
.row-btn:hover .arrow { transform: translateX(2px); }
.row-btn.danger { color: #c44536; border-color: rgba(196,68,54,0.3); }
.row-btn.danger:hover { background: #fef2f2; border-color: #c44536; }
.pager-row { display: flex; justify-content: flex-end; margin-top: 24px; padding-top: 16px; border-top: 1px solid #ebebeb; }
</style>

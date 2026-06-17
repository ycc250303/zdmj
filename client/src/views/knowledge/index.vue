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
  { label: $t('page.knowledge.typeProjectDoc'), value: 1 }
  // type=2 GitHub 暂不支持
  // type=3 DeepWiki 暂不支持
]);

const knowledgeTypeLabels = computed<Record<number, { label: string; type: 'primary' | 'info' | 'success' | 'default' | 'warning' }>>(() => ({
  1: { label: $t('page.knowledge.typeProjectDoc'), type: 'primary' },
  2: { label: 'GitHub', type: 'info' },
  3: { label: 'DeepWiki', type: 'warning' }
}));

const embeddingStatusLabels = computed<Record<string, { text: string; type: 'success' | 'warning' | 'error' | 'default' }>>(() => ({
  PENDING: { text: $t('page.knowledge.embeddingPending'), type: 'default' },
  RUNNING: { text: $t('page.knowledge.embeddingRunning'), type: 'warning' },
  SUCCESS: { text: $t('page.knowledge.embeddingSuccess'), type: 'success' },
  FAILED: { text: $t('page.knowledge.embeddingFailed'), type: 'error' }
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
  <div class="nova-page">
    <div v-if="!isEditing" class="nova-page__inner">
      <header class="nova-page__head">
        <div>
          <span class="nova-eyebrow">// knowledge</span>
          <h1 class="nova-page__title font-display">{{ $t('page.knowledge.title') }}</h1>
          <p class="nova-page__sub">上传文档自动切分、向量化（pgvector）入库，参与对话时按需召回。</p>
        </div>
        <NButton type="primary" size="medium" @click="handleAddNew">
          <template #icon>
            <icon-carbon-add class="text-16px" />
          </template>
          {{ $t('page.knowledge.addBtn') }}
        </NButton>
      </header>

      <section class="nova-toolbar">
        <NSelect
          v-model:value="searchParams.type"
          :options="knowledgeTypeOptions"
          :placeholder="$t('page.knowledge.type')"
          clearable
          class="w-40"
        />
        <NButton type="primary" @click="handleSearch">
          <template #icon>
            <icon-carbon-search class="text-14px" />
          </template>
          {{ $t('common.search') }}
        </NButton>
        <NButton @click="handleReset">{{ $t('common.reset') }}</NButton>
      </section>

      <NSpin :show="loading">
        <template v-if="loading">
          <div v-for="i in 5" :key="i" class="nova-doc-card nova-doc-card--skeleton">
            <NSkeleton height="22px" width="60%" class="mb-3" />
            <NSkeleton height="14px" width="40%" class="mb-2" />
            <NSkeleton height="14px" width="80%" />
          </div>
        </template>

        <div v-else-if="knowledgeList.length === 0" class="nova-empty">
          <span class="nova-empty__icon">
            <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 4h12a4 4 0 0 1 4 4v12H8a4 4 0 0 1-4-4V4Z" />
              <path d="M4 16a4 4 0 0 1 4-4h12" />
              <path d="M8 8h8M8 12h5" />
            </svg>
          </span>
          <p class="nova-empty__text">{{ $t('page.profile.common.empty') }}</p>
          <NButton type="primary" @click="handleAddNew">
            <template #icon>
              <icon-carbon-add class="text-14px" />
            </template>
            {{ $t('page.knowledge.createFirst') }}
          </NButton>
        </div>

        <div v-else class="flex flex-col gap-3">
          <article v-for="item in knowledgeList" :key="item.id" class="nova-doc-card">
            <div class="nova-doc-card__main">
              <div class="nova-doc-card__head">
                <span class="nova-doc-card__icon">
                  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                    <path d="M14 2v6h6" />
                    <path d="M9 13h6M9 17h6M9 9h2" />
                  </svg>
                </span>
                <h3 class="nova-doc-card__title">{{ item.title }}</h3>
                <NTag :type="knowledgeTypeLabels[item.type]?.type" size="small" round>
                  {{ knowledgeTypeLabels[item.type]?.label || $t('page.knowledge.unknown') }}
                </NTag>
                <NTag :type="getEmbeddingStatus(item).type" size="small" round>
                  {{ getEmbeddingStatus(item).text }}
                </NTag>
              </div>
              <div class="nova-doc-card__path">{{ item.content }}</div>
              <div v-if="item.lastError" class="nova-doc-card__error">{{ item.lastError }}</div>
            </div>
            <div class="nova-doc-card__actions">
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
          </article>
        </div>

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

    <div v-else class="py-4">
      <KnowledgeForm :initial-data="currentEditData" @success="onFormSuccess" @cancel="isEditing = false" />
    </div>

    <KnowledgeDetail :knowledge-id="currentDetailId" :show="showDetail" @update:show="showDetail = $event" />
  </div>
</template>

<style scoped>
.nova-page {
  position: relative;
  min-height: 100%;
  padding: 32px 32px 60px;
  color: var(--nova-text);
}

.nova-page__inner {
  max-width: 1080px;
  margin: 0 auto;
}

.nova-page__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.nova-page__title {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(120deg, #fff 0%, #c9c4ff 60%, #93f1ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.nova-page__sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--nova-text-faded);
  max-width: 640px;
  line-height: 1.6;
}

.nova-eyebrow {
  display: inline-block;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--nova-violet);
  text-transform: lowercase;
}

.nova-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.015));
  border: 1px solid var(--nova-border);
  backdrop-filter: blur(20px);
  margin-bottom: 20px;
}

.nova-doc-card {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  padding: 18px 20px;
  border-radius: 14px;
  border: 1px solid var(--nova-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0.018));
  backdrop-filter: blur(20px) saturate(1.05);
  transition: border-color 0.3s ease, transform 0.25s ease;
}

.nova-doc-card:hover {
  border-color: var(--nova-border-strong);
  transform: translateY(-1px);
}

.nova-doc-card--skeleton {
  display: block;
}

.nova-doc-card__main {
  flex: 1;
  min-width: 0;
}

.nova-doc-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}

.nova-doc-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: rgba(124, 92, 255, 0.14);
  color: var(--nova-violet);
  border: 1px solid rgba(124, 92, 255, 0.28);
  flex-shrink: 0;
}

.nova-doc-card__title {
  font-size: 15.5px;
  font-weight: 600;
  color: #fff;
  letter-spacing: -0.005em;
  margin: 0;
}

.nova-doc-card__path {
  font-size: 12.5px;
  color: var(--nova-text-faded);
  font-family: 'JetBrains Mono', monospace;
  letter-spacing: 0.01em;
  word-break: break-all;
  line-height: 1.5;
}

.nova-doc-card__error {
  margin-top: 8px;
  font-size: 12.5px;
  color: var(--nova-coral);
  background: rgba(251, 113, 133, 0.08);
  border-left: 2px solid var(--nova-coral);
  padding: 6px 10px;
  border-radius: 4px;
}

.nova-doc-card__actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.nova-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  border-radius: 18px;
  border: 1px dashed var(--nova-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.025), rgba(255, 255, 255, 0.008));
  text-align: center;
  gap: 14px;
}

.nova-empty__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: rgba(124, 92, 255, 0.1);
  border: 1px solid rgba(124, 92, 255, 0.22);
  color: var(--nova-violet);
}

.nova-empty__text {
  color: var(--nova-text-faded);
  font-size: 13.5px;
}
</style>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { fetchGetKnowledgeDocumentDetail } from '@/service/api/knowledge';
import type { KnowledgeApi } from '@/service/api/knowledge';
import { $t } from '@/locales';

interface Props {
  knowledgeId: number;
  show: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits(['update:show']);

const loading = ref(true);
const detail = ref<KnowledgeApi.KnowledgeDocumentDTO | null>(null);

const knowledgeTypeLabels: Record<number, { label: string; type: 'primary' | 'info' | 'warning' }> = {
  1: { label: '项目文档', type: 'primary' },
  2: { label: 'GitHub 代码', type: 'info' },
  3: { label: 'DeepWiki', type: 'warning' }
};

const showModal = computed({
  get: () => props.show,
  set: (val) => emit('update:show', val)
});

const embeddingStatusLabels: Record<string, { text: string; type: 'success' | 'warning' | 'error' | 'default' }> = {
  PENDING: { text: '等待中', type: 'default' },
  RUNNING: { text: '向量化中...', type: 'warning' },
  SUCCESS: { text: '已向量化', type: 'success' },
  FAILED: { text: '向量化失败', type: 'error' }
};

async function loadDetail() {
  if (!props.knowledgeId) return;

  loading.value = true;
  try {
    const { data, error } = await fetchGetKnowledgeDocumentDetail(props.knowledgeId);
    if (!error && data) {
      detail.value = data;
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
  if (!detail.value?.content) return $t('page.knowledge.openFile');
  if (isGitHubUrl(detail.value.content)) return $t('page.knowledge.viewRepo');
  if (isPdfUrl(detail.value.content)) return $t('page.knowledge.downloadFile');
  return $t('page.knowledge.openFile');
});

const buttonIcon = computed(() => {
  if (!detail.value?.content) return '';
  if (isGitHubUrl(detail.value.content)) return 'github';
  if (isPdfUrl(detail.value.content)) return 'download';
  return 'open';
});

const embeddingInfo = computed(() => {
  if (!detail.value) return null;

  const status = detail.value.embeddingStatus || 'PENDING';
  const statusInfo = embeddingStatusLabels[status] || embeddingStatusLabels.PENDING;

  return {
    status,
    statusText: statusInfo.text,
    statusType: statusInfo.type,
    lastEmbeddedAt: detail.value.lastEmbeddedAt,
    lastError: detail.value.lastError
  };
});

watch(
  () => props.show,
  (newVal) => {
    if (newVal) {
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
    :title="$t('page.knowledge.title')"
    :style="{ width: '800px', maxHeight: '80vh' }"
    :segmented="{ content: 'soft' }"
  >
    <NSpin :show="loading">
      <div v-if="detail" class="space-y-6">
        <!-- 基本信息 -->
        <div>
          <h3 class="text-lg font-bold text-gray-800 mb-4 flex items-center gap-2">
            <span class="text-blue-500">ℹ️</span>
            {{ $t('page.knowledge.basicInfo') }}
          </h3>
          <NDescriptions :column="1" bordered label-placement="left" label-style="width: 120px">
            <NDescriptionsItem :label="$t('page.knowledge.docTitle')">
              {{ detail.title }}
            </NDescriptionsItem>
            <NDescriptionsItem :label="$t('page.knowledge.type')">
              <NTag :type="knowledgeTypeLabels[detail.type]?.type" size="small">
                {{ knowledgeTypeLabels[detail.type]?.label || $t('page.knowledge.unknown') }}
              </NTag>
            </NDescriptionsItem>
            <NDescriptionsItem :label="$t('page.knowledge.content')">
              <a :href="detail.content" target="_blank" class="text-blue-500 hover:underline truncate block max-w-md">
                {{ detail.content }}
              </a>
            </NDescriptionsItem>
            <NDescriptionsItem v-if="detail.createdAt" label="创建时间">
              {{ new Date(detail.createdAt).toLocaleString('zh-CN') }}
            </NDescriptionsItem>
            <NDescriptionsItem v-if="detail.updatedAt" label="更新时间">
              {{ new Date(detail.updatedAt).toLocaleString('zh-CN') }}
            </NDescriptionsItem>
          </NDescriptions>
        </div>

        <!-- 向量化信息 -->
        <div v-if="embeddingInfo">
          <h3 class="text-lg font-bold text-gray-800 mb-4 flex items-center gap-2">
            <span class="text-purple-500">⬚</span>
            {{ $t('page.knowledge.vectorInfo') }}
          </h3>
          <NDescriptions :column="1" bordered label-placement="left" label-style="width: 120px">
            <NDescriptionsItem :label="$t('page.knowledge.status')">
              <NTag :type="embeddingInfo.statusType" size="small" round>
                {{ embeddingInfo.statusText }}
              </NTag>
            </NDescriptionsItem>
            <NDescriptionsItem v-if="embeddingInfo.lastEmbeddedAt" :label="$t('page.knowledge.lastVectorTime')">
              {{ new Date(embeddingInfo.lastEmbeddedAt).toLocaleString('zh-CN') }}
            </NDescriptionsItem>
            <NDescriptionsItem v-if="embeddingInfo.lastError" :label="$t('page.knowledge.errorMsg')">
              <span class="text-red-500">{{ embeddingInfo.lastError }}</span>
            </NDescriptionsItem>
          </NDescriptions>
        </div>

      </div>
      <NEmpty v-else :description="$t('common.noData')" />
    </NSpin>

    <template #footer>
      <div class="flex justify-end gap-3">
        <NButton type="primary" @click="handleDownloadContent">
          <template #icon>
            <icon-carbon-launch v-if="buttonIcon === 'open'" class="text-14px" />
            <icon-carbon-download v-else-if="buttonIcon === 'download'" class="text-14px" />
            <icon-carbon-logo-github v-else-if="buttonIcon === 'github'" class="text-14px" />
            <icon-carbon-arrow-up-right v-else class="text-14px" />
          </template>
          {{ buttonText }}
        </NButton>
        <NButton @click="showModal = false">{{ $t('common.close') }}</NButton>
      </div>
    </template>
  </NModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  fetchDownloadKnowledgeDocument,
  fetchGetKnowledgeDocumentDetail,
  fetchRetryKnowledgeEmbedding
} from '@/service/api/knowledge';
import type { KnowledgeApi } from '@/service/api/knowledge';
import { $t } from '@/locales';

interface Props {
  knowledgeId: number;
  show: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits(['update:show', 'refresh']);

const loading = ref(true);
const detail = ref<KnowledgeApi.KnowledgeDocumentDTO | null>(null);

const knowledgeTypeLabels: Record<number, { label: string; type: 'primary' | 'info' | 'warning' }> = {
  1: { label: '项目文档', type: 'primary' },
  2: { label: 'GitHub 代码', type: 'info' },
  3: { label: 'DeepWiki', type: 'warning' }
};

const showModal = computed({
  get: () => props.show,
  set: val => emit('update:show', val)
});

// 向量化状态映射
const embeddingStatusLabels: Record<
  string,
  { text: string; type: 'success' | 'warning' | 'error' | 'default'; icon: string }
> = {
  PENDING: { text: '等待中', type: 'default', icon: '🕐' },
  RUNNING: { text: '向量化中...', type: 'warning', icon: '⏳' },
  SUCCESS: { text: '已向量化', type: 'success', icon: '✅' },
  FAILED: { text: '向量化失败', type: 'error', icon: '⚠️' }
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

const downloading = ref(false);
const processing = ref(false);

async function handleDownloadContent() {
  if (!detail.value?.id) return;
  downloading.value = true;
  try {
    const { data, error } = await fetchDownloadKnowledgeDocument(detail.value.id);
    if (error || !data) return;
    const url = URL.createObjectURL(data);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = String(detail.value.metadata?.fileName || detail.value.title);
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } finally {
    downloading.value = false;
  }
}

async function handleRetry() {
  if (!detail.value?.id) return;
  processing.value = true;
  try {
    const { error } = await fetchRetryKnowledgeEmbedding(detail.value.id);
    if (!error) window.$message?.success('文档向量化成功');
    await loadDetail();
    emit('refresh');
  } finally {
    processing.value = false;
  }
}

const buttonText = computed(() => $t('page.knowledge.downloadFile'));
const buttonIcon = computed(() => '⬇️');

const embeddingInfo = computed(() => {
  if (!detail.value) return null;

  const status = detail.value.embeddingStatus || 'PENDING';
  const statusInfo = embeddingStatusLabels[status] || embeddingStatusLabels.PENDING;

  return {
    status,
    statusText: statusInfo.text,
    statusType: statusInfo.type,
    statusIcon: statusInfo.icon,
    lastEmbeddedAt: detail.value.lastEmbeddedAt,
    lastError: detail.value.lastError
  };
});

watch(
  () => props.show,
  newVal => {
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
          <h3 class="mb-4 flex items-center gap-2 text-lg text-gray-800 font-bold">
            <span class="text-blue-500">ℹ️</span>
            {{ $t('page.knowledge.basicInfo') }}
          </h3>
          <NDescriptions :column="1" bordered label-placement="left" label-style="width: 120px">
            <NDescriptionsItem label="原文件状态">
              {{
                {
                  0: '正在保存',
                  1: '可以下载',
                  2: '保存失败，可删除记录；替换失败可重新处理以恢复原文件',
                  3: '正在删除，可重试删除'
                }[detail.fileStatus]
              }}
            </NDescriptionsItem>
            <NDescriptionsItem :label="$t('page.knowledge.docTitle')">
              {{ detail.title }}
            </NDescriptionsItem>
            <NDescriptionsItem :label="$t('page.knowledge.type')">
              <NTag :type="knowledgeTypeLabels[detail.type]?.type" size="small">
                {{ knowledgeTypeLabels[detail.type]?.label || $t('page.knowledge.unknown') }}
              </NTag>
            </NDescriptionsItem>
            <NDescriptionsItem :label="$t('page.knowledge.content')">
              <span>{{ detail.metadata?.fileName || detail.title }}</span>
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
          <h3 class="mb-4 flex items-center gap-2 text-lg text-gray-800 font-bold">
            <span class="text-purple-500">⬚</span>
            {{ $t('page.knowledge.vectorInfo') }}
          </h3>
          <NDescriptions :column="1" bordered label-placement="left" label-style="width: 120px">
            <NDescriptionsItem :label="$t('page.knowledge.status')">
              <NTag :type="embeddingInfo.statusType" size="small">
                <template #icon>
                  <div :class="embeddingInfo.statusIcon"></div>
                </template>
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
        <NButton
          type="primary"
          :loading="downloading"
          :disabled="detail?.fileStatus !== 1"
          @click="handleDownloadContent"
        >
          <template #icon>
            <div :class="buttonIcon"></div>
          </template>
          {{ buttonText }}
        </NButton>
        <NButton
          v-if="
            (detail?.fileStatus === 1 && detail.embeddingStatus !== 'SUCCESS') ||
            (detail?.fileStatus === 2 && detail.metadata?.canRestoreOriginal)
          "
          :loading="processing"
          @click="handleRetry"
        >
          重新处理
        </NButton>
        <NButton @click="showModal = false">{{ $t('common.close') }}</NButton>
      </div>
    </template>
  </NModal>
</template>

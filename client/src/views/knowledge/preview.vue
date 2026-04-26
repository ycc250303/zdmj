<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { fetchGetKnowledgeDetail } from '@/service/api/knowledge';
import type { KnowledgeApi } from '@/service/api/knowledge';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const detail = ref<KnowledgeApi.KnowledgeDTO | null>(null);

const knowledgeId = computed(() => Number(route.query.id));

async function loadDetail() {
  if (!knowledgeId.value) return;

  loading.value = true;
  try {
    const { data, error } = await fetchGetKnowledgeDetail(knowledgeId.value);
    if (!error && data) {
      detail.value = data;
    }
  } catch (err) {
    console.error('加载详情失败:', err);
    window.$message?.error('加载详情失败');
  } finally {
    loading.value = false;
  }
}

function goBack() {
  router.back();
}

onMounted(() => {
  loadDetail();
});
</script>

<template>
  <div class="h-screen flex flex-col bg-gray-900">
    <!-- 顶部工具栏 -->
    <div class="bg-gray-800 text-white p-3 flex items-center gap-3 border-b border-gray-700">
      <NButton quaternary circle @click="goBack" class="text-white">
        <template #icon>
          <div class="i-mdi-arrow-left text-xl"></div>
        </template>
      </NButton>
      <div class="flex-1 truncate">
        <span v-if="detail">{{ detail.name }}</span>
        <span v-else>加载中...</span>
      </div>
      <a v-if="detail" :href="detail.content" target="_blank" class="text-white hover:text-blue-400">
        <NButton size="small" secondary>
          <template #icon>
            <div class="i-mdi-download"></div>
          </template>
          下载文件
        </NButton>
      </a>
    </div>

    <!-- 内容预览区 -->
    <NSpin :show="loading" class="flex-1">
      <div v-if="detail && detail.content" class="h-full">
        <iframe
          :src="detail.content"
          class="w-full h-full border-0"
          :type="detail.content.toLowerCase().includes('.pdf') ? 'application/pdf' : undefined"
        ></iframe>
      </div>
      <div v-else class="h-full flex items-center justify-center text-white">
        <NEmpty description="暂无内容" />
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
:deep(.n-spin) {
  height: 100%;
}
</style>

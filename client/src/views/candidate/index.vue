<!-- <script setup lang="ts">
import { ref, onMounted } from 'vue';
import { request } from '@/service/request';
import { $t } from '@/locales';

// 规范：定义组件名
defineOptions({
  name: 'CandidateIndex'
});

// 控制按钮的加载状态
const loading = ref(false);

async function fetchCandidateData() {
  loading.value = true;
  try {
    window.$message?.info('开始向 Apifox 发送请求...');
    
    const res = await request({ 
      url: '/resumes', 
      method: 'GET' 
    });
    
    console.log('🎉 成功拿到后端数据啦！数据内容是：', res);
    window.$message?.success($t('page.candidate.successMsg'));

  } catch (error) {
    console.error('❌ 请求失败了，快检查一下：', error);
  } finally {
    loading.value = false;
  }
}

// 供按钮点击触发的函数
function handleTestClick() {
  fetchCandidateData();
}

onMounted(() => {
  fetchCandidateData();
});
</script>

<template>
  <div class="h-full p-16px">
    <NCard :title="$t('page.candidate.title')" :bordered="false" class="h-full rounded-16px shadow-sm">
      
      <NSpace vertical :size="24">
        
        <NGradientText type="primary" :size="24">
          {{ $t('page.candidate.welcome') }}
        </NGradientText>
        
        <NAlert type="info" show-icon>
          {{ $t('page.candidate.hint') }}
        </NAlert>

        <div class="mt-4">
          <NButton type="primary" size="large" :loading="loading" @click="handleTestClick">
            <template #icon>
              <div class="i-mdi-send-variant"></div>
            </template>
            {{ $t('page.candidate.fetchData') }}
          </NButton>
        </div>

      </NSpace>
    </NCard>
  </div>
</template>

<style scoped></style> -->
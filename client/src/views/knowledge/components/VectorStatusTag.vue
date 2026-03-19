<script setup lang="ts">
import { computed } from 'vue';
import type { KnowledgeApi } from '@/service/api/knowledge';

interface Props {
  status: KnowledgeApi.TaskStatus;
}

const props = defineProps<Props>();

const statusConfig = computed(() => {
  switch (props.status) {
    case 1:
      return { type: 'warning' as const, label: '待处理', icon: 'i-mdi-clock-outline' };
    case 2:
      return { type: 'info' as const, label: '向量化中', icon: 'i-mdi-loading animate-spin' };
    case 3:
      return { type: 'success' as const, label: '已完成', icon: 'i-mdi-check-circle' };
    case 4:
      return { type: 'error' as const, label: '失败', icon: 'i-mdi-alert-circle' };
    case 5:
      return { type: 'default' as const, label: '已取消', icon: 'i-mdi-cancel' };
    default:
      return { type: 'default' as const, label: '未知', icon: 'i-mdi-help-circle' };
  }
});
</script>

<template>
  <NTag :type="statusConfig.type" size="small">
    <template #icon>
      <div :class="statusConfig.icon" class="mr-1"></div>
    </template>
    {{ statusConfig.label }}
  </NTag>
</template>

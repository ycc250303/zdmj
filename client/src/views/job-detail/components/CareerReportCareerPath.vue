<script setup lang="ts">
import { NTag } from 'naive-ui';

defineOptions({ name: 'CareerReportCareerPath' });

interface CareerPathData {
  entryPoint: string;
  nextStep: string;
  transitionLogic: string;
  alternativePaths: string[];
}

defineProps<{ data: CareerPathData }>();
</script>

<template>
  <div class="airbnb-card p-4 mb-4">
    <h4 class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-3">职业路径</h4>

    <div class="flex flex-col sm:flex-row items-center gap-2 sm:gap-4 mb-4">
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg px-4 py-2.5 text-center min-w-[140px]">
        <div class="text-xs text-[#6a6a6a] dark:text-gray-400 font-medium mb-0.5">入职起点</div>
        <div class="text-sm font-semibold text-[#222222] dark:text-gray-200">{{ data.entryPoint }}</div>
      </div>
      <div class="text-[#c4a46c] text-xl">→</div>
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg px-4 py-2.5 text-center min-w-[140px]">
        <div class="text-xs text-[#6a6a6a] dark:text-gray-400 font-medium mb-0.5">下一步发展</div>
        <div class="text-sm font-semibold text-[#222222] dark:text-gray-200">{{ data.nextStep }}</div>
      </div>
    </div>

    <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg p-3 mb-3">
      <div class="text-xs font-semibold text-[#222222] dark:text-gray-200 mb-1">过渡逻辑</div>
      <p class="text-sm text-[#3f3f3f] dark:text-gray-300 leading-relaxed">{{ data.transitionLogic }}</p>
    </div>

    <div v-if="data.alternativePaths.length">
      <div class="text-xs font-semibold text-[#222222] dark:text-gray-200 mb-2">备选路径</div>
      <div class="flex flex-wrap gap-2">
        <NTag v-for="(alt, i) in data.alternativePaths" :key="i" type="default" round size="small">{{ alt }}</NTag>
      </div>
    </div>
  </div>
</template>

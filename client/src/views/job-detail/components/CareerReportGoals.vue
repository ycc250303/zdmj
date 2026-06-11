<script setup lang="ts">
import { NTag } from 'naive-ui';

defineOptions({ name: 'CareerReportGoals' });

interface GoalsData {
  shortTerm: string;
  midTerm: string;
  longTerm: string;
}

defineProps<{ data: GoalsData }>();
</script>

<template>
  <div class="airbnb-card p-4 mb-4">
    <h4 class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-3">职业目标</h4>

    <div class="relative">
      <div class="absolute left-3 top-2 bottom-2 w-0.5 bg-[#c4a46c]/30"></div>
      <div class="space-y-4">
        <div v-if="data.shortTerm" class="flex items-start gap-3 relative">
          <span class="bg-[#c4a46c] text-white text-xs font-bold rounded-full w-6 h-6 flex items-center justify-center shrink-0 z-10">1</span>
          <div class="border border-[#ebebeb] dark:border-gray-700 rounded-lg p-3 flex-1">
            <div class="flex items-center gap-2 mb-1">
              <span class="font-semibold text-sm text-[#222222] dark:text-gray-200">短期目标</span>
              <NTag size="tiny" type="warning" round>6个月内</NTag>
            </div>
            <p class="text-sm text-[#3f3f3f] dark:text-gray-300">{{ data.shortTerm }}</p>
          </div>
        </div>
        <div v-if="data.midTerm" class="flex items-start gap-3 relative">
          <span class="bg-[#c4a46c] text-white text-xs font-bold rounded-full w-6 h-6 flex items-center justify-center shrink-0 z-10">2</span>
          <div class="border border-[#ebebeb] dark:border-gray-700 rounded-lg p-3 flex-1">
            <div class="flex items-center gap-2 mb-1">
              <span class="font-semibold text-sm text-[#222222] dark:text-gray-200">中期目标</span>
              <NTag size="tiny" type="error" round>2年内</NTag>
            </div>
            <p class="text-sm text-[#3f3f3f] dark:text-gray-300">{{ data.midTerm }}</p>
          </div>
        </div>
        <div v-if="data.longTerm" class="flex items-start gap-3 relative">
          <span class="bg-[#c4a46c] text-white text-xs font-bold rounded-full w-6 h-6 flex items-center justify-center shrink-0 z-10">3</span>
          <div class="border border-[#ebebeb] dark:border-gray-700 rounded-lg p-3 flex-1">
            <div class="flex items-center gap-2 mb-1">
              <span class="font-semibold text-sm text-[#222222] dark:text-gray-200">长期目标</span>
              <NTag size="tiny" type="info" round>5年内</NTag>
            </div>
            <p class="text-sm text-[#3f3f3f] dark:text-gray-300">{{ data.longTerm }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

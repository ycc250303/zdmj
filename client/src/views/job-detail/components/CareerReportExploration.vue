<script setup lang="ts">
defineOptions({ name: 'CareerReportExploration' });

interface ExplorationData {
  currentRole: string;
  industry: string;
  company: string;
  location: string;
  salaryRange: string;
}

defineProps<{ data: ExplorationData }>();
</script>

<template>
  <div class="airbnb-card p-4 mb-4">
    <h4 class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-3">职业探索</h4>
    <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg p-3">
        <div class="text-xs text-[#6a6a6a] dark:text-gray-400 mb-1">当前岗位</div>
        <div class="text-sm font-semibold text-[#222222] dark:text-gray-200">{{ data.currentRole }}</div>
      </div>
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg p-3">
        <div class="text-xs text-[#6a6a6a] dark:text-gray-400 mb-1">所属行业</div>
        <div class="text-sm font-semibold text-[#222222] dark:text-gray-200">{{ data.industry }}</div>
      </div>
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg p-3">
        <div class="text-xs text-[#6a6a6a] dark:text-gray-400 mb-1">公司</div>
        <div class="text-sm font-semibold text-[#222222] dark:text-gray-200">{{ data.company }}</div>
      </div>
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg p-3">
        <div class="text-xs text-[#6a6a6a] dark:text-gray-400 mb-1">地点 / 薪资</div>
        <div class="text-sm font-semibold text-[#222222] dark:text-gray-200">{{ data.location }} · {{ data.salaryRange }}</div>
      </div>
    </div>
  </div>
</template>

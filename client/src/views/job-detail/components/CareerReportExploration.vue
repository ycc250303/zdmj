<script setup lang="ts">
defineOptions({ name: 'CareerReportExploration' });

interface ExplorationData {
  roleClarity: string;
  industryInsight: string;
  marketPositioning: string;
}

defineProps<{ data: ExplorationData }>();
</script>

<template>
  <div class="airbnb-card p-4 mb-4">
    <h4 class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-3">职业探索</h4>
    <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg p-3">
        <div class="font-semibold text-sm text-[#222222] dark:text-gray-200 mb-2">角色定位</div>
        <p class="text-xs text-[#3f3f3f] dark:text-gray-300 leading-relaxed">{{ data.roleClarity }}</p>
      </div>
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg p-3">
        <div class="font-semibold text-sm text-[#222222] dark:text-gray-200 mb-2">行业洞察</div>
        <p class="text-xs text-[#3f3f3f] dark:text-gray-300 leading-relaxed">{{ data.industryInsight }}</p>
      </div>
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg p-3">
        <div class="font-semibold text-sm text-[#222222] dark:text-gray-200 mb-2">市场定位</div>
        <p class="text-xs text-[#3f3f3f] dark:text-gray-300 leading-relaxed">{{ data.marketPositioning }}</p>
      </div>
    </div>
  </div>
</template>

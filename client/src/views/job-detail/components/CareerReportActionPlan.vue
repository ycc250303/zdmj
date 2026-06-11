<script setup lang="ts">
defineOptions({ name: 'CareerReportActionPlan' });

interface ActionTask {
  task: string;
  cycle: string;
  deliverable: string;
  verification: string;
}

interface ActionPlanData {
  shortTerm: ActionTask[];
  midTerm: ActionTask[];
}

defineProps<{ data: ActionPlanData }>();
</script>

<template>
  <div class="airbnb-card p-4 mb-4">
    <h4 class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-3">行动计划</h4>

    <!-- 短期 -->
    <div v-if="data.shortTerm.length" class="mb-4">
      <div class="flex items-center gap-2 mb-3">
        <span class="font-semibold text-sm text-[#222222] dark:text-gray-200">短期计划</span>
        <NTag size="tiny" type="warning" round>近期执行</NTag>
      </div>
      <div class="space-y-3">
        <div v-for="(t, i) in data.shortTerm" :key="i" class="border-l-2 border-[#ff385c] pl-3 py-1">
          <div class="flex items-start gap-2 mb-1">
            <span class="bg-[#ff385c] text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center shrink-0">{{ i + 1 }}</span>
            <span class="text-sm font-medium text-[#222222] dark:text-gray-200">{{ t.task }}</span>
          </div>
          <div class="grid grid-cols-3 gap-2 ml-7 text-xs text-[#6a6a6a] dark:text-gray-400">
            <div>周期：{{ t.cycle }}</div>
            <div>交付物：{{ t.deliverable }}</div>
            <div>验证：{{ t.verification }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 中期 -->
    <div v-if="data.midTerm.length">
      <div class="flex items-center gap-2 mb-3">
        <span class="font-semibold text-sm text-[#222222] dark:text-gray-200">中期计划</span>
        <NTag size="tiny" type="info" round>持续成长</NTag>
      </div>
      <div class="space-y-3">
        <div v-for="(t, i) in data.midTerm" :key="i" class="border-l-2 border-[#ff385c] pl-3 py-1">
          <div class="flex items-start gap-2 mb-1">
            <span class="bg-[#ff385c] text-white text-xs font-bold rounded-full w-5 h-5 flex items-center justify-center shrink-0">{{ i + 1 }}</span>
            <span class="text-sm font-medium text-[#222222] dark:text-gray-200">{{ t.task }}</span>
          </div>
          <div class="grid grid-cols-3 gap-2 ml-7 text-xs text-[#6a6a6a] dark:text-gray-400">
            <div>周期：{{ t.cycle }}</div>
            <div>交付物：{{ t.deliverable }}</div>
            <div>验证：{{ t.verification }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

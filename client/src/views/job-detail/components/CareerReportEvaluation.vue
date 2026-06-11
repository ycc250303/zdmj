<script setup lang="ts">
import { NTag } from 'naive-ui';

defineOptions({ name: 'CareerReportEvaluation' });

interface EvaluationMetric {
  metric: string;
  target: string;
  deadline: string;
}

interface EvaluationData {
  cycles: string[];
  metrics: EvaluationMetric[];
}

defineProps<{ data: EvaluationData }>();
</script>

<template>
  <div class="airbnb-card p-4 mb-4">
    <h4 class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-3">评估计划</h4>

    <div v-if="data.cycles.length" class="mb-4">
      <div class="text-xs font-semibold text-[#222222] dark:text-gray-200 mb-2">评估周期</div>
      <div class="flex flex-wrap gap-2">
        <NTag v-for="(c, i) in data.cycles" :key="i" type="success" round size="small">{{ c }}</NTag>
      </div>
    </div>

    <div v-if="data.metrics.length">
      <div class="text-xs font-semibold text-[#222222] dark:text-gray-200 mb-2">量化指标</div>
      <div class="overflow-x-auto">
        <table class="w-full text-xs border-collapse">
          <thead>
            <tr class="bg-[#f7f7f7] dark:bg-gray-700/40">
              <th class="text-left p-2.5 rounded-l-lg font-semibold text-[#222222] dark:text-gray-200">指标</th>
              <th class="text-center p-2.5 font-semibold text-[#222222] dark:text-gray-200">目标值</th>
              <th class="text-right p-2.5 rounded-r-lg font-semibold text-[#222222] dark:text-gray-200">截止时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(m, i) in data.metrics" :key="i" class="border-b border-[#ebebeb] dark:border-gray-700/50 last:border-none">
              <td class="p-2.5 text-[#3f3f3f] dark:text-gray-300">{{ m.metric }}</td>
              <td class="p-2.5 text-center">
                <NTag size="tiny" :type="m.target === '100%' || m.target === '达成' ? 'success' : 'warning'" round>{{ m.target }}</NTag>
              </td>
              <td class="p-2.5 text-right text-[#6a6a6a] dark:text-gray-400">{{ m.deadline }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

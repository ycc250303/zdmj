<script setup lang="ts">
import { NTag } from 'naive-ui';

defineOptions({ name: 'CareerReportEvaluation' });

interface EvaluationMetric {
  key: string;
  label: string;
  target: string;
}

interface EvaluationData {
  evaluationCycle: string;
  metrics: EvaluationMetric[];
}

defineProps<{ data: EvaluationData }>();
</script>

<template>
  <div class="airbnb-card p-4 mb-4">
    <h4 class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-3">评估计划</h4>

    <div v-if="data.evaluationCycle" class="mb-4">
      <div class="text-xs font-semibold text-[#222222] dark:text-gray-200 mb-2">评估周期</div>
      <NTag type="success" round size="small">{{ data.evaluationCycle }}</NTag>
    </div>

    <div v-if="data.metrics.length">
      <div class="text-xs font-semibold text-[#222222] dark:text-gray-200 mb-2">量化指标</div>
      <div class="overflow-x-auto">
        <table class="w-full text-xs border-collapse">
          <thead>
            <tr class="bg-[#f7f7f7] dark:bg-gray-700/40">
              <th class="text-left p-2.5 rounded-l-lg font-semibold text-[#222222] dark:text-gray-200">指标</th>
              <th class="text-right p-2.5 rounded-r-lg font-semibold text-[#222222] dark:text-gray-200">目标值</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(m, i) in data.metrics" :key="i" class="border-b border-[#ebebeb] dark:border-gray-700/50 last:border-none">
              <td class="p-2.5 text-[#3f3f3f] dark:text-gray-300">{{ m.key.replace(/([A-Z])/g, ' $1').replace(/^./, (s) => s.toUpperCase()).trim() }}</td>
              <td class="p-2.5 text-right text-[#3f3f3f] dark:text-gray-300">{{ m.target }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

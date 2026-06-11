<script setup lang="ts">
defineOptions({ name: 'CareerReportEvidenceCard' });

interface EvidenceData {
  industry: string[];
  technical: string[];
  communication: string[];
}

defineProps<{ data: EvidenceData }>();
</script>

<template>
  <div class="airbnb-card p-4 mb-4">
    <h4 class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-3">证据支撑</h4>
    <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg p-3">
        <div class="font-semibold text-sm text-[#222222] dark:text-gray-200 mb-2">行业证据</div>
        <ul class="space-y-1">
          <li v-for="(item, i) in data.industry" :key="i" class="text-xs text-[#3f3f3f] dark:text-gray-300 flex items-start gap-1.5">
            <span class="text-[#ff385c] mt-0.5 shrink-0">-</span><span>{{ item }}</span>
          </li>
        </ul>
      </div>
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg p-3">
        <div class="font-semibold text-sm text-[#222222] dark:text-gray-200 mb-2">技术证据</div>
        <ul class="space-y-1">
          <li v-for="(item, i) in data.technical" :key="i" class="text-xs text-[#3f3f3f] dark:text-gray-300 flex items-start gap-1.5">
            <span class="text-[#ff385c] mt-0.5 shrink-0">-</span><span>{{ item }}</span>
          </li>
        </ul>
      </div>
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg p-3">
        <div class="font-semibold text-sm text-[#222222] dark:text-gray-200 mb-2">沟通证据</div>
        <ul class="space-y-1">
          <li v-for="(item, i) in data.communication" :key="i" class="text-xs text-[#3f3f3f] dark:text-gray-300 flex items-start gap-1.5">
            <span class="text-[#ff385c] mt-0.5 shrink-0">-</span><span>{{ item }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

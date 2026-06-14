<script setup lang="ts">
import { NTag } from 'naive-ui';

defineOptions({ name: 'CareerReportCareerPath' });

interface TransitionPath {
  name?: string;
  targetRole?: string;
  reason?: string;
  difficulty?: string;
  bridgingSkills?: string[];
}

interface CareerPathData {
  currentLevel: string;
  nextLevel: string;
  transitionPaths: TransitionPath[];
}

defineProps<{ data: CareerPathData }>();

const difficultyLabel: Record<string, string> = { low: '低', medium: '中', high: '高' };
const difficultyType: Record<string, string> = { low: 'success', medium: 'warning', high: 'error' };
</script>

<template>
  <div class="airbnb-card p-4 mb-4">
    <h4 class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-3">职业路径</h4>

    <div class="flex flex-col sm:flex-row items-center gap-2 sm:gap-4 mb-4">
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg px-4 py-2.5 text-center min-w-[140px]">
        <div class="text-xs text-[#6a6a6a] dark:text-gray-400 font-medium mb-0.5">当前层级</div>
        <div class="text-sm font-semibold text-[#222222] dark:text-gray-200">{{ data.currentLevel }}</div>
      </div>
      <div class="text-[#c4a46c] text-xl">→</div>
      <div class="bg-[#f7f7f7] dark:bg-gray-700/30 rounded-lg px-4 py-2.5 text-center min-w-[140px]">
        <div class="text-xs text-[#6a6a6a] dark:text-gray-400 font-medium mb-0.5">下一阶段</div>
        <div class="text-sm font-semibold text-[#222222] dark:text-gray-200">{{ data.nextLevel }}</div>
      </div>
    </div>

    <div v-if="data.transitionPaths.length">
      <div class="text-xs font-semibold text-[#222222] dark:text-gray-200 mb-3">过渡路径</div>
      <div class="space-y-3">
        <div
          v-for="(p, i) in data.transitionPaths"
          :key="i"
          class="border border-[#ebebeb] dark:border-gray-700 rounded-lg p-3"
        >
          <div class="flex items-center gap-2 mb-2 flex-wrap">
            <span class="font-semibold text-sm text-[#222222] dark:text-gray-200">{{ p.name }}</span>
            <NTag size="tiny" round>→ {{ p.targetRole }}</NTag>
            <NTag :type="difficultyType[p.difficulty || ''] || 'default'" size="tiny" round>
              难度: {{ difficultyLabel[p.difficulty || ''] || p.difficulty }}
            </NTag>
          </div>
          <p class="text-xs text-[#3f3f3f] dark:text-gray-300 mb-2">{{ p.reason }}</p>
          <div v-if="p.bridgingSkills?.length" class="flex gap-1.5 flex-wrap">
            <span
              v-for="sk in p.bridgingSkills"
              :key="sk"
              class="text-xs px-2 py-0.5 rounded-full bg-[#f7f7f7] dark:bg-gray-700/40 text-[#6a6a6a] dark:text-gray-400"
            >{{ sk }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

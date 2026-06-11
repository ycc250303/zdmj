<script setup lang="ts">
/**
 * 能力维度列表组件
 *
 * 替代原来 5-7 个彩色嵌套框的"框套框"布局。
 * Airbnb 风格：按硬技能/软技能分组，每条用 2px 珊瑚红左边线。
 */
import { computed } from 'vue';
import { $t } from '@/locales';

export interface AbilityItem {
  key: string;
  label: string;
  value?: string;
  icon?: string;
  /** 'hard' | 'soft'，用于分组 */
  category: 'hard' | 'soft';
  score?: number;
  max?: number;
}

interface Props {
  items: AbilityItem[];
}

const props = defineProps<Props>();

const hardSkills = computed(() => props.items.filter(i => i.category === 'hard'));
const softSkills = computed(() => props.items.filter(i => i.category === 'soft'));

const groupLabel = {
  hard: '硬技能',
  soft: '软技能'
} as const;
</script>

<template>
  <div class="space-y-6">
    <!-- 硬技能分组 -->
    <div v-if="hardSkills.length">
      <h4 class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-3 px-1">
        <span class="i-ph-wrench w-4 h-4 inline-block mr-1.5 text-[#ff385c]"></span>
        {{ groupLabel.hard }}
      </h4>
      <div class="space-y-0">
        <div
          v-for="(item, idx) in hardSkills"
          :key="item.key"
          class="px-4 py-3 border-l-2 transition-colors"
          :class="[
            idx < hardSkills.length - 1 ? 'border-b border-[#ebebeb] dark:border-gray-700/50' : '',
            'border-l-[#ff385c] hover:border-l-[#e00b41]'
          ]"
          :style="{ borderLeftColor: '#ff385c' }"
        >
          <div class="flex items-center gap-2 mb-1">
            <span class="text-sm font-medium text-[#222222] dark:text-gray-200">{{ item.label }}</span>
            <span v-if="item.score != null && item.max != null" class="text-xs text-[#ff385c] font-semibold">{{ item.score }}/{{ item.max }}</span>
          </div>
          <p v-if="item.value" class="text-sm text-[#3f3f3f] dark:text-gray-300 leading-relaxed">
            {{ item.value }}
          </p>
          <p v-else class="text-sm text-[#929292] dark:text-gray-500 italic">
            暂无相关信息
          </p>
        </div>
      </div>
    </div>

    <!-- 软技能分组 -->
    <div v-if="softSkills.length">
      <h4 class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-3 px-1">
        <span class="i-ph-heart w-4 h-4 inline-block mr-1.5 text-[#ff385c]"></span>
        {{ groupLabel.soft }}
      </h4>
      <div class="space-y-0">
        <div
          v-for="(item, idx) in softSkills"
          :key="item.key"
          class="px-4 py-3 border-l-2 transition-colors"
          :class="[
            idx < softSkills.length - 1 ? 'border-b border-[#ebebeb] dark:border-gray-700/50' : '',
          ]"
          :style="{ borderLeftColor: '#ff385c' }"
        >
          <div class="flex items-center gap-2 mb-1">
            <span class="text-sm font-medium text-[#222222] dark:text-gray-200">{{ item.label }}</span>
            <span v-if="item.score != null && item.max != null" class="text-xs text-[#ff385c] font-semibold">{{ item.score }}/{{ item.max }}</span>
          </div>
          <p v-if="item.value" class="text-sm text-[#3f3f3f] dark:text-gray-300 leading-relaxed">
            {{ item.value }}
          </p>
          <p v-else class="text-sm text-[#929292] dark:text-gray-500 italic">
            暂无相关信息
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 能力维度 gauge 进度条
 *
 * Airbnb 风格：浅灰底 + 珊瑚红填充，全圆角，极简。
 */
interface Props {
  /** 维度标签 */
  label: string;
  /** 当前分数 */
  score: number;
  /** 满分 */
  max: number;
  /** 可选描述文本 */
  description?: string;
}

const props = defineProps<Props>();

function percent(): number {
  if (!props.max) return 0;
  return Math.min(100, Math.round((props.score / props.max) * 100));
}
</script>

<template>
  <div class="flex items-center gap-3">
    <span class="text-sm font-medium text-[#222222] dark:text-gray-200 w-24 shrink-0">{{ label }}</span>
    <div class="flex-1 h-2 bg-[#f7f7f7] dark:bg-gray-700 rounded-full overflow-hidden">
      <div
        class="h-full rounded-full transition-all duration-500"
        style="background: linear-gradient(90deg, #ff385c, #ff6b81)"
        :style="{ width: percent() + '%' }"
      ></div>
    </div>
    <span class="text-xs text-[#6a6a6a] dark:text-gray-400 w-16 text-right">{{ score }}/{{ max }}</span>
    <span v-if="description" class="text-xs text-[#929292] dark:text-gray-500 w-48 truncate">{{ description }}</span>
  </div>
</template>

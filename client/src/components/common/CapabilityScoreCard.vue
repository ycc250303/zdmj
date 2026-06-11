<script setup lang="ts">
/**
 * 画像评分卡组件
 *
 * Airbnb 风格：珊瑚红主色，纯白卡片，极简排版。
 *  - 左侧：核心评价 + 总分 + 优势亮点 chips
 *  - 右侧：echarts 雷达图 + 各维度进度条
 */
import { computed, onMounted, onBeforeUnmount, ref, shallowRef, watch, nextTick } from 'vue';
import * as echarts from 'echarts/core';
import { RadarChart } from 'echarts/charts';
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([RadarChart, TitleComponent, TooltipComponent, LegendComponent, CanvasRenderer]);

export interface Dimension {
  key: string;
  label: string;
  score: number;
  max: number;
}

interface Props {
  dimensions: Dimension[];
  totalScore?: number;
  totalMax?: number;
  totalLabel?: string;
  summary?: string;
  summaryLabel?: string;
  strengths?: string[];
  strengthsLabel?: string;
  generatedAt?: string;
  themeColor?: string;
}

const props = withDefaults(defineProps<Props>(), {
  totalMax: 100,
  totalLabel: '总分',
  summaryLabel: '核心评价',
  strengthsLabel: '优势亮点',
  themeColor: '#ff385c'
});

const chartEl = ref<HTMLDivElement | null>(null);
const chart = shallowRef<echarts.ECharts | null>(null);

function buildOption() {
  const indicators = props.dimensions.map(d => ({
    name: d.label,
    max: d.max
  }));
  const values = props.dimensions.map(d => d.score);

  return {
    tooltip: { trigger: 'item' },
    radar: {
      indicator: indicators,
      shape: 'polygon',
      center: ['50%', '54%'],
      radius: '58%',
      splitNumber: 4,
      axisName: {
        color: '#475569',
        fontSize: 12,
        fontWeight: 500,
        padding: [3, 6]
      },
      splitArea: {
        areaStyle: {
          color: ['rgba(255,56,92,0.02)', 'rgba(255,56,92,0.05)']
        }
      },
      splitLine: {
        lineStyle: { color: '#e2e8f0' }
      },
      axisLine: {
        lineStyle: { color: '#e2e8f0' }
      }
    },
    series: [
      {
        type: 'radar',
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: props.themeColor, width: 2 },
        itemStyle: { color: props.themeColor },
        areaStyle: { color: props.themeColor, opacity: 0.25 },
        data: [{ value: values, name: props.totalLabel }]
      }
    ]
  };
}

function renderChart() {
  if (!chartEl.value) return;
  if (!chart.value) {
    chart.value = echarts.init(chartEl.value);
  }
  chart.value.setOption(buildOption(), true);
}

function handleResize() {
  chart.value?.resize();
}

onMounted(async () => {
  await nextTick();
  renderChart();
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  chart.value?.dispose();
  chart.value = null;
});

watch(
  () => props.dimensions,
  () => renderChart(),
  { deep: true }
);

function percentOf(d: Dimension): number {
  if (!d.max) return 0;
  return Math.min(100, Math.round((d.score / d.max) * 100));
}

const hasStrengths = computed(() => Array.isArray(props.strengths) && props.strengths.length > 0);

const isMinimal = computed(() => !props.summary && !hasStrengths.value);

const scoreLevel = computed(() => {
  const score = props.totalScore ?? 0;
  const ratio = props.totalMax ? score / props.totalMax : 0;
  if (ratio >= 0.85) return { text: '优秀', cls: 'bg-rose-100 text-rose-700', tip: '表现非常出色，继续保持！' };
  if (ratio >= 0.7) return { text: '良好', cls: 'bg-rose-50 text-rose-600', tip: '整体不错，仍有上升空间' };
  if (ratio >= 0.5) return { text: '中等', cls: 'bg-amber-50 text-amber-600', tip: '基础尚可，建议针对性补强' };
  return { text: '待提升', cls: 'bg-gray-100 text-gray-600', tip: '需要重点优化提升' };
});
const scoreLevelText = computed(() => scoreLevel.value.text);
const scoreLevelClass = computed(() => scoreLevel.value.cls);
const scoreLevelTip = computed(() => scoreLevel.value.tip);

const RING_CIRC = 2 * Math.PI * 54;
const ringDashOffset = computed(() => {
  const ratio = props.totalMax ? Math.min(1, (props.totalScore ?? 0) / props.totalMax) : 0;
  return RING_CIRC * (1 - ratio);
});

const gradId = `csc-grad-${Math.random().toString(36).slice(2, 8)}`;
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 items-stretch">
    <!-- 左侧 -->
    <div
      class="relative overflow-hidden rounded-lg border border-[#ebebeb] dark:border-gray-700 p-6 flex flex-col"
      :class="isMinimal
        ? 'bg-[#fafafa] dark:bg-gray-800/40 items-center justify-center text-center gap-6'
        : 'bg-[#ffffff] dark:bg-gray-800 gap-5'"
    >
      <!-- 装饰光斑（仅 minimal 模式） -->
      <template v-if="isMinimal">
        <div class="absolute -top-12 -right-12 w-44 h-44 rounded-full bg-[#ff385c]/8 blur-3xl pointer-events-none"></div>
        <div class="absolute -bottom-16 -left-10 w-52 h-52 rounded-full bg-[#ff385c]/6 blur-3xl pointer-events-none"></div>
      </template>

      <!-- Minimal 模式：环形大评分卡 -->
      <template v-if="isMinimal">
        <div class="relative z-10 text-[#222222] dark:text-gray-200 font-semibold flex items-center gap-2">
          <span>{{ totalLabel }}</span>
        </div>

        <div class="relative z-10">
          <svg class="-rotate-90" width="180" height="180" viewBox="0 0 120 120">
            <circle cx="60" cy="60" r="54" fill="none" stroke="#ebebeb" stroke-width="10" />
            <defs>
              <linearGradient :id="gradId" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" :stop-color="themeColor" />
                <stop offset="100%" stop-color="#ff6b81" />
              </linearGradient>
            </defs>
            <circle
              cx="60"
              cy="60"
              r="54"
              fill="none"
              :stroke="`url(#${gradId})`"
              stroke-width="10"
              stroke-linecap="round"
              :stroke-dasharray="RING_CIRC"
              :stroke-dashoffset="ringDashOffset"
              style="transition: stroke-dashoffset 0.8s ease;"
            />
          </svg>
          <div class="absolute inset-0 flex flex-col items-center justify-center">
            <div class="text-4xl font-bold text-[#222222] dark:text-gray-200 leading-none">{{ totalScore ?? 0 }}</div>
            <div class="text-xs text-[#929292] dark:text-gray-400 mt-1">/ {{ totalMax }}</div>
          </div>
        </div>

        <div class="relative z-10 flex flex-col items-center gap-2">
          <span class="px-3 py-1 text-xs rounded-full font-medium" :class="scoreLevelClass">
            {{ scoreLevelText }}
          </span>
          <p class="text-sm text-[#6a6a6a] dark:text-gray-400">{{ scoreLevelTip }}</p>
        </div>

        <div v-if="generatedAt" class="relative z-10 text-xs text-[#929292] dark:text-gray-500">
          分析时间：{{ generatedAt }}
        </div>
      </template>

      <!-- 正常模式 -->
      <template v-else>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2 text-[#222222] dark:text-gray-200 font-semibold">
            <span>{{ summaryLabel }}</span>
          </div>
          <span v-if="generatedAt" class="text-xs text-[#929292] dark:text-gray-500">{{ generatedAt }}</span>
        </div>

        <p v-if="summary" class="text-[#3f3f3f] dark:text-gray-300 leading-relaxed text-sm whitespace-pre-wrap">
          {{ summary }}
        </p>

        <div class="flex items-center gap-4 bg-[#f7f7f7] dark:bg-gray-700/40 rounded-lg p-4">
          <div
            class="w-16 h-16 rounded-full flex items-center justify-center text-white font-bold text-lg shadow-md flex-shrink-0"
            :style="{ background: `linear-gradient(135deg, ${themeColor}, #ff6b81)` }"
          >
            {{ totalScore ?? 0 }}
          </div>
          <div class="flex-1">
            <div class="text-xs text-[#6a6a6a] dark:text-gray-400 mb-1">{{ totalLabel }}</div>
            <div class="flex items-baseline gap-1 flex-wrap">
              <span class="text-2xl font-bold text-[#222222] dark:text-gray-200">{{ totalScore ?? 0 }}</span>
              <span class="text-sm text-[#929292] dark:text-gray-400">/ {{ totalMax }}</span>
              <span
                class="ml-2 px-2 py-0.5 text-xs rounded-full"
                :class="scoreLevelClass"
              >{{ scoreLevelText }}</span>
            </div>
          </div>
        </div>

        <div v-if="hasStrengths" class="flex-1">
          <div class="text-[#222222] dark:text-gray-200 font-semibold text-sm mb-3 flex items-center gap-1">
            <span>{{ strengthsLabel }}</span>
            <span class="text-xs text-[#929292] dark:text-gray-500 font-normal ml-1">({{ strengths!.length }})</span>
          </div>
          <div class="flex flex-wrap gap-2">
            <span
              v-for="(item, i) in strengths"
              :key="i"
              class="inline-block px-3 py-1.5 rounded-lg bg-[#f7f7f7] dark:bg-gray-700/40 text-[#222222] dark:text-gray-200 text-xs border border-[#ebebeb] dark:border-gray-600 hover:shadow-sm transition"
            >
              {{ item }}
            </span>
          </div>
        </div>
      </template>
    </div>

    <!-- 右侧：雷达图 + 维度进度条 -->
    <div class="bg-[#ffffff] dark:bg-gray-800 border border-[#ebebeb] dark:border-gray-700 rounded-lg p-6 flex flex-col gap-5">
      <div class="flex items-center gap-2 text-[#222222] dark:text-gray-200 font-semibold">
        <span>多维度评分</span>
      </div>

      <div ref="chartEl" class="w-full h-[260px] px-2"></div>

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
        <div v-for="(d, i) in dimensions" :key="d.key">
          <div class="flex justify-between items-center text-xs text-[#3f3f3f] dark:text-gray-400 mb-1.5">
            <span class="font-medium">{{ d.label }}</span>
            <span class="font-semibold text-[#222222] dark:text-gray-200">{{ d.score }}/{{ d.max }}</span>
          </div>
          <div class="h-2 w-full bg-[#f7f7f7] dark:bg-gray-700 rounded-full overflow-hidden">
            <div
              class="h-full rounded-full transition-all duration-500"
              :style="{ width: percentOf(d) + '%', background: `linear-gradient(90deg, ${themeColor}, #ff6b81)` }"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

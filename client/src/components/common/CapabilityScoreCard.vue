<script setup lang="ts">
/**
 * 画像评分卡组件
 *
 * 通用展示能力画像/岗位画像的评分总览，参照设计稿：
 *  - 左侧：核心评价（一段总结）+ 总分 + 优势亮点 chips
 *  - 右侧：echarts 雷达图 + 各维度进度条
 *
 * 数据约定：
 *  - dimensions: 维度数组 [{ key, label, score, max }]，自动渲染雷达图与进度条
 *  - totalScore / totalMax: 总分（左下大字）
 *  - summary: 核心评价文本
 *  - strengths: 优势亮点（「能力标签 — 具体证据」列表）
 *  - generatedAt: 分析时间显示
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
  /** 唯一 key */
  key: string;
  /** 中文展示标签 */
  label: string;
  /** 当前分数 */
  score: number;
  /** 满分 */
  max: number;
}

interface Props {
  /** 维度评分（建议 4~6 个） */
  dimensions: Dimension[];
  /** 总分 */
  totalScore?: number;
  /** 总分上限，默认 100 */
  totalMax?: number;
  /** 总分标签，如 "综合竞争力" / "综合匹配度" */
  totalLabel?: string;
  /** 核心评价文本 */
  summary?: string;
  /** 核心评价标题，默认 "核心评价" */
  summaryLabel?: string;
  /** 优势亮点（每条建议「能力标签 — 具体证据」） */
  strengths?: string[];
  /** 优势亮点标题，默认 "优势亮点" */
  strengthsLabel?: string;
  /** 分析时间（已格式化字符串） */
  generatedAt?: string;
  /** 雷达图主色（hex / rgba） */
  themeColor?: string;
}

const props = withDefaults(defineProps<Props>(), {
  totalMax: 100,
  totalLabel: '总分',
  summaryLabel: '核心评价',
  strengthsLabel: '优势亮点',
  themeColor: '#7c5cff'
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
          color: ['rgba(124, 92, 255, 0.02)', 'rgba(124, 92, 255, 0.05)']
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
        areaStyle: { color: props.themeColor, opacity: 0.35 },
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

// --- 进度条颜色按维度循环 ---
const progressColors = ['#7c5cff', '#22c55e', '#3b82f6', '#06b6d4', '#f59e0b', '#ec4899'];

function colorOf(idx: number): string {
  return progressColors[idx % progressColors.length];
}

function percentOf(d: Dimension): number {
  if (!d.max) return 0;
  return Math.min(100, Math.round((d.score / d.max) * 100));
}

const hasStrengths = computed(() => Array.isArray(props.strengths) && props.strengths.length > 0);

interface ParsedStrength {
  label: string;
  detail: string;
}

function parseStrengthItem(item: string): ParsedStrength {
  const trimmed = item.trim();
  const sep = trimmed.includes(' — ') ? ' — ' : (trimmed.includes(' - ') ? ' - ' : null);
  if (!sep) {
    return { label: trimmed, detail: '' };
  }
  const idx = trimmed.indexOf(sep);
  return {
    label: trimmed.slice(0, idx).trim(),
    detail: trimmed.slice(idx + sep.length).trim()
  };
}

const parsedStrengths = computed(() =>
  (props.strengths ?? []).map(parseStrengthItem)
);

/** 是否处于"仅有总分"的极简状态：用于切换左卡为居中大评分卡 */
const isMinimal = computed(() => !props.summary && !hasStrengths.value);

// 总分等级（用于胶囊上的标签）
const scoreLevel = computed(() => {
  const score = props.totalScore ?? 0;
  const ratio = props.totalMax ? score / props.totalMax : 0;
  if (ratio >= 0.85) return { text: '优秀', cls: 'bg-emerald-100 text-emerald-700', tip: '表现非常出色，继续保持！' };
  if (ratio >= 0.7) return { text: '良好', cls: 'bg-blue-100 text-blue-700', tip: '整体不错，仍有上升空间' };
  if (ratio >= 0.5) return { text: '中等', cls: 'bg-amber-100 text-amber-700', tip: '基础尚可，建议针对性补强' };
  return { text: '待提升', cls: 'bg-rose-100 text-rose-700', tip: '需要重点优化提升' };
});
const scoreLevelText = computed(() => scoreLevel.value.text);
const scoreLevelClass = computed(() => scoreLevel.value.cls);
const scoreLevelTip = computed(() => scoreLevel.value.tip);

/** 环形进度的 stroke-dashoffset（圆周长 2πr，r=54 → ≈339.29） */
const RING_CIRC = 2 * Math.PI * 54;
const ringDashOffset = computed(() => {
  const ratio = props.totalMax ? Math.min(1, (props.totalScore ?? 0) / props.totalMax) : 0;
  return RING_CIRC * (1 - ratio);
});

/** SVG 渐变 id（每个组件实例唯一，避免多个组件同时存在时冲突） */
const gradId = `csc-grad-${Math.random().toString(36).slice(2, 8)}`;
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 items-stretch">
    <!-- 左侧：根据是否有 summary/strengths 切换布局 -->
    <div
      class="relative overflow-hidden rounded-2xl border border-emerald-100 p-6 flex flex-col"
      :class="isMinimal
        ? 'bg-gradient-to-br from-emerald-50 via-teal-50/60 to-cyan-50/40 items-center justify-center text-center gap-6'
        : 'bg-gradient-to-br from-emerald-50 to-teal-50/40 gap-5'"
    >
      <!-- 装饰光斑（仅 minimal 模式） -->
      <template v-if="isMinimal">
        <div class="absolute -top-12 -right-12 w-44 h-44 rounded-full bg-emerald-300/20 blur-3xl pointer-events-none"></div>
        <div class="absolute -bottom-16 -left-10 w-52 h-52 rounded-full bg-teal-300/20 blur-3xl pointer-events-none"></div>
      </template>

      <!-- ============ Minimal 模式：环形大评分卡 ============ -->
      <template v-if="isMinimal">
        <div class="relative z-10 text-emerald-700 font-semibold flex items-center gap-2">
          <span class="text-lg">🏆</span>
          <span>{{ totalLabel }}</span>
        </div>

        <!-- SVG 环形进度 -->
        <div class="relative z-10">
          <svg class="-rotate-90" width="180" height="180" viewBox="0 0 120 120">
            <!-- 背景圆环 -->
            <circle cx="60" cy="60" r="54" fill="none" stroke="#e2e8f0" stroke-width="10" />
            <!-- 渐变 -->
            <defs>
              <linearGradient :id="gradId" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" :stop-color="themeColor" />
                <stop offset="100%" stop-color="#22c55e" />
              </linearGradient>
            </defs>
            <!-- 进度圆环 -->
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
          <!-- 圆心数字 -->
          <div class="absolute inset-0 flex flex-col items-center justify-center">
            <div class="text-4xl font-bold text-slate-800 leading-none">{{ totalScore ?? 0 }}</div>
            <div class="text-xs text-slate-400 mt-1">/ {{ totalMax }}</div>
          </div>
        </div>

        <div class="relative z-10 flex flex-col items-center gap-2">
          <span class="px-3 py-1 text-xs rounded-full font-medium" :class="scoreLevelClass">
            {{ scoreLevelText }}
          </span>
          <p class="text-sm text-slate-600">{{ scoreLevelTip }}</p>
        </div>

        <div v-if="generatedAt" class="relative z-10 text-xs text-slate-400">
          分析时间：{{ generatedAt }}
        </div>
      </template>

      <!-- ============ 正常模式：标题 + 评价 + 总分胶囊 + 优势亮点 ============ -->
      <template v-else>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2 text-emerald-700 font-semibold">
            <span class="text-lg">📝</span>
            <span>{{ summaryLabel }}</span>
          </div>
          <span v-if="generatedAt" class="text-xs text-slate-500">{{ generatedAt }}</span>
        </div>

        <p v-if="summary" class="text-slate-700 leading-relaxed text-sm whitespace-pre-wrap">
          {{ summary }}
        </p>

        <div class="flex items-center gap-4 bg-white/80 backdrop-blur rounded-xl p-4 border border-emerald-100 shadow-sm">
          <div
            class="w-16 h-16 rounded-full flex items-center justify-center text-white font-bold text-lg shadow-md flex-shrink-0"
            :style="{ background: `linear-gradient(135deg, ${themeColor}, #22c55e)` }"
          >
            {{ totalScore ?? 0 }}
          </div>
          <div class="flex-1">
            <div class="text-xs text-slate-500 mb-1">{{ totalLabel }}</div>
            <div class="flex items-baseline gap-1 flex-wrap">
              <span class="text-2xl font-bold text-slate-800">{{ totalScore ?? 0 }}</span>
              <span class="text-sm text-slate-400">/ {{ totalMax }}</span>
              <span
                class="ml-2 px-2 py-0.5 text-xs rounded-full"
                :class="scoreLevelClass"
              >{{ scoreLevelText }}</span>
            </div>
          </div>
        </div>

        <div v-if="hasStrengths" class="flex-1">
          <div class="text-emerald-700 font-semibold text-sm mb-3 flex items-center gap-1">
            <span>✨</span>
            <span>{{ strengthsLabel }}</span>
            <span class="text-xs text-slate-400 font-normal ml-1">({{ strengths!.length }})</span>
          </div>
          <ul class="space-y-2.5">
            <li
              v-for="(item, i) in parsedStrengths"
              :key="i"
              class="flex items-start gap-2 text-sm leading-relaxed"
            >
              <span class="text-emerald-500 mt-0.5 flex-shrink-0">•</span>
              <span class="min-w-0">
                <span class="font-semibold text-emerald-800">{{ item.label }}</span>
                <span v-if="item.detail" class="text-slate-600"> — {{ item.detail }}</span>
              </span>
            </li>
          </ul>
        </div>
      </template>
    </div>

    <!-- 右侧：雷达图 + 各维度进度条 -->
    <div class="bg-white border border-slate-200 rounded-2xl p-6 flex flex-col gap-5">
      <div class="flex items-center gap-2 text-slate-700 font-semibold">
        <span class="text-lg">🎯</span>
        <span>多维度评分</span>
      </div>

      <div ref="chartEl" class="w-full h-[260px] px-2"></div>

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
        <div v-for="(d, i) in dimensions" :key="d.key">
          <div class="flex justify-between items-center text-xs text-slate-600 mb-1.5">
            <span class="font-medium">{{ d.label }}</span>
            <span class="font-semibold text-slate-700">{{ d.score }}/{{ d.max }}</span>
          </div>
          <div class="h-2 w-full bg-slate-100 rounded-full overflow-hidden">
            <div
              class="h-full rounded-full transition-all duration-500"
              :style="{ width: percentOf(d) + '%', background: colorOf(i) }"
            ></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/auth';
import { useAppStore } from '@/store/modules/app';
import { $t } from '@/locales';

defineOptions({ name: 'HomeDashboard' });

const router = useRouter();
const authStore = useAuthStore();
const appStore = useAppStore();

const isZh = computed(() => appStore.locale === 'zh-CN');

// ====== greeting ======
const now = ref(new Date());
let timer = 0 as unknown as ReturnType<typeof setInterval>;

onMounted(() => {
  timer = setInterval(() => (now.value = new Date()), 30_000);
});
onBeforeUnmount(() => clearInterval(timer));

const greeting = computed(() => {
  const h = now.value.getHours();
  if (isZh.value) {
    if (h < 5) return '夜深了';
    if (h < 11) return '早上好';
    if (h < 14) return '中午好';
    if (h < 18) return '下午好';
    return '晚上好';
  }
  if (h < 5) return 'Still up';
  if (h < 11) return 'Good morning';
  if (h < 14) return 'Good noon';
  if (h < 18) return 'Good afternoon';
  return 'Good evening';
});

const dateLine = computed(() =>
  now.value.toLocaleDateString(isZh.value ? 'zh-CN' : 'en-US', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
);

const userName = computed(() => authStore.userInfo.userName || (isZh.value ? '指挥官' : 'Captain'));

// ====== quick actions (bento) ======
interface Action {
  key: 'agent' | 'knowledge' | 'resume' | 'role';
  title: string;
  desc: string;
  accent: string;
  accentSoft: string;
  badge: string;
  span: 'wide' | 'tall' | 'normal';
  route: string;
}

const actions = computed<Action[]>(() => [
  {
    key: 'agent',
    title: $t('page.home.aiChat.title'),
    desc: $t('page.home.aiChat.description'),
    accent: '#7C5CFF',
    accentSoft: 'rgba(124, 92, 255, 0.18)',
    badge: 'agent',
    span: 'wide',
    route: '/chat'
  },
  {
    key: 'knowledge',
    title: $t('page.home.knowledge.title'),
    desc: $t('page.home.knowledge.description'),
    accent: '#22D3EE',
    accentSoft: 'rgba(34, 211, 238, 0.18)',
    badge: 'rag',
    span: 'tall',
    route: '/knowledge'
  },
  {
    key: 'resume',
    title: $t('page.home.resume.title'),
    desc: $t('page.home.resume.description'),
    accent: '#FB7185',
    accentSoft: 'rgba(251, 113, 133, 0.18)',
    badge: 'evolve',
    span: 'normal',
    route: '/resume-import'
  },
  {
    key: 'role',
    title: $t('page.home.jobs.title'),
    desc: $t('page.home.jobs.description'),
    accent: '#34D399',
    accentSoft: 'rgba(52, 211, 153, 0.18)',
    badge: 'pulse',
    span: 'normal',
    route: '/jobs'
  }
]);

// ====== workflow steps (real product flow) ======
interface WorkflowStep {
  k: string;
  step: string;
  title: string;
  desc: string;
  route: string;
  cta: string;
}

const workflow = computed<WorkflowStep[]>(() => {
  const z = isZh.value;
  return [
    {
      k: 'wf-1',
      step: '01',
      title: z ? '上传简历' : 'Upload resume',
      desc: z
        ? '上传 PDF 简历，系统会自动结构化解析教育经历、项目、技能等信息，并生成学生能力画像。'
        : 'Upload a PDF resume — auto-parsed into education, projects and skills, then a capability profile.',
      route: '/resume-import',
      cta: z ? '前往简历分析' : 'Go to resume'
    },
    {
      k: 'wf-2',
      step: '02',
      title: z ? '构建知识库' : 'Build knowledge base',
      desc: z
        ? '上传学习资料、面经、笔记等文档，系统自动切分并向量化入库，参与后续 RAG 问答。'
        : 'Upload notes and study materials — auto-chunked and embedded into pgvector for RAG.',
      route: '/knowledge',
      cta: z ? '管理知识库' : 'Manage knowledge'
    },
    {
      k: 'wf-3',
      step: '03',
      title: z ? '与 AI 对话' : 'Chat with AI',
      desc: z
        ? '在对话中开启 RAG 引用你的知识库，获得有依据的求职建议与岗位解读。'
        : 'Toggle RAG in chat to ground answers in your private knowledge base.',
      route: '/chat',
      cta: z ? '开始对话' : 'Start chat'
    },
    {
      k: 'wf-4',
      step: '04',
      title: z ? '岗位匹配' : 'Match a role',
      desc: z
        ? '选择目标岗位，查看岗位画像并与个人能力画像匹配，输出能力差距与晋升路径。'
        : 'Pick a target role, view its profile and match against you for gaps and progression paths.',
      route: '/jobs',
      cta: z ? '浏览岗位' : 'Browse roles'
    }
  ];
});

function go(route: string) {
  router.push(route);
}
</script>

<template>
  <div class="nova-home">
    <AuroraStage variant="panel" :grid="true" :vignette="false" />

    <div class="nova-home__inner">
      <!-- ============= HEADER ROW ============= -->
      <header class="nova-home__head">
        <div>
          <span class="nova-home__eyebrow">
            <span class="nova-home__eyebrow-dot" />
            <span class="font-mono">nova://workspace</span>
          </span>
          <h1 class="nova-home__title font-display">
            {{ greeting }},
            <span class="nova-text-gradient">{{ userName }}</span>
          </h1>
          <p class="nova-home__sub">{{ dateLine }} · {{ $t('page.home.hero.subtitle') }}</p>
        </div>
        <div class="nova-home__head-actions">
          <button class="nova-home-btn nova-home-btn--ghost" type="button" @click="go('/knowledge')">
            <icon-carbon-add class="text-16px" />
            <span>{{ isZh ? '上传知识' : 'Upload knowledge' }}</span>
          </button>
          <button class="nova-home-btn nova-home-btn--primary" type="button" @click="go('/chat')">
            <icon-carbon-chat class="text-16px" />
            <span>{{ $t('page.home.hero.startChat') }}</span>
          </button>
        </div>
      </header>

      <!-- ============= MAIN GRID (BENTO + SIDEBAR) ============= -->
      <section class="nova-home__main">
        <div class="nova-bento-grid">
          <article
            v-for="a in actions"
            :key="a.key"
            class="nova-bento"
            :class="`nova-bento--${a.span}`"
            :style="{ '--card-accent': a.accent, '--card-soft': a.accentSoft } as any"
            tabindex="0"
            @click="go(a.route)"
            @keydown.enter="go(a.route)"
          >
            <div class="nova-bento__halo" />
            <div class="nova-bento__head">
              <span class="nova-bento__icon">
                <svg v-if="a.key === 'agent'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M5 8h14a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H8l-4 3v-3a2 2 0 0 1-2-2v-3" />
                  <path d="M9 13h.01M12 13h.01M15 13h.01" />
                  <path d="M12 4v3" />
                </svg>
                <svg v-else-if="a.key === 'knowledge'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V3H6.5A2.5 2.5 0 0 0 4 5.5v14Z" />
                  <path d="M4 19.5A2.5 2.5 0 0 0 6.5 22H20" />
                  <path d="M9 8h7M9 12h5" />
                </svg>
                <svg v-else-if="a.key === 'resume'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M7 3h7l4 4v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z" />
                  <path d="M14 3v4h4M9 12h7M9 16h7" />
                </svg>
                <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 2L4 8v8l8 6 8-6V8l-8-6Z" />
                  <path d="M12 11v4M9 13l3 2 3-2" />
                </svg>
              </span>
              <span class="nova-bento__badge font-mono">{{ a.badge }}</span>
            </div>
            <div class="nova-bento__body">
              <h3 class="nova-bento__title font-display">{{ a.title }}</h3>
              <p class="nova-bento__desc">{{ a.desc }}</p>
            </div>
          </article>
        </div>

        <!-- ============= WORKFLOW GUIDE ============= -->
        <aside class="nova-feed">
          <div class="nova-feed__head">
            <span class="nova-eyebrow">// workflow</span>
            <h3 class="nova-feed__title font-display">
              {{ isZh ? '推荐使用流程' : 'Recommended workflow' }}
            </h3>
            <p class="nova-feed__sub">
              {{ isZh ? '按以下顺序使用各模块，效果最佳。' : 'Follow these steps in order for the best experience.' }}
            </p>
          </div>

          <ol class="nova-feed__steps">
            <li v-for="w in workflow" :key="w.k" class="nova-feed__step" @click="go(w.route)">
              <span class="nova-feed__step-no font-display">{{ w.step }}</span>
              <div class="nova-feed__step-body">
                <div class="nova-feed__step-title">{{ w.title }}</div>
                <div class="nova-feed__step-desc">{{ w.desc }}</div>
                <div class="nova-feed__step-cta">
                  <span>{{ w.cta }}</span>
                  <icon-carbon-arrow-right class="text-12px" />
                </div>
              </div>
            </li>
          </ol>
        </aside>
      </section>
    </div>
  </div>
</template>

<style scoped>
.nova-home {
  position: relative;
  min-height: calc(100% + 32px);
  margin: -16px;
  color: var(--nova-text);
  background: var(--nova-bg-deep);
  overflow: hidden;
  border-radius: 0;
}

.nova-home__inner {
  position: relative;
  z-index: 5;
  padding: 32px 36px 56px;
  max-width: 1480px;
  margin: 0 auto;
}

/* ===== Eyebrow shared ===== */
.nova-eyebrow {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  background: rgba(124, 92, 255, 0.12);
  border: 1px solid rgba(124, 92, 255, 0.28);
  color: #b9b1ff;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  letter-spacing: 0.16em;
  margin-bottom: 12px;
}

/* ============ Header ============ */
.nova-home__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
  padding-bottom: 28px;
  margin-bottom: 28px;
  border-bottom: 1px solid var(--nova-border);
}

.nova-home__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.02);
  font-size: 11.5px;
  letter-spacing: 0.06em;
  color: var(--nova-text-faded);
  margin-bottom: 14px;
}

.nova-home__eyebrow-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--nova-violet);
  box-shadow: 0 0 12px var(--nova-violet);
  animation: nova-pulse 2.4s ease-in-out infinite;
}

@keyframes nova-pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50%      { transform: scale(1.4); opacity: 0.55; }
}

.nova-home__title {
  font-size: clamp(30px, 4vw, 44px);
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #fff;
  line-height: 1.1;
}

.nova-home__sub {
  margin-top: 8px;
  font-size: 14px;
  color: var(--nova-text-soft);
  max-width: 640px;
}

.nova-home__head-actions {
  display: flex;
  gap: 10px;
}

.nova-home-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 11px 18px;
  border-radius: 12px;
  font-size: 13.5px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: transform 0.2s ease, box-shadow 0.25s ease, background 0.25s ease,
    border-color 0.25s ease, color 0.25s ease;
  white-space: nowrap;
}

.nova-home-btn--primary {
  color: #fff;
  background: linear-gradient(135deg, #7c5cff 0%, #5b4cff 50%, #22d3ee 130%);
  box-shadow: 0 12px 28px -8px rgba(124, 92, 255, 0.55), inset 0 1px 0 rgba(255, 255, 255, 0.18);
}

.nova-home-btn--primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 38px -6px rgba(124, 92, 255, 0.65), inset 0 1px 0 rgba(255, 255, 255, 0.24);
}

.nova-home-btn--ghost {
  color: var(--nova-text);
  background: var(--nova-glass);
  border-color: var(--nova-border);
  backdrop-filter: blur(8px);
}

.nova-home-btn--ghost:hover {
  border-color: var(--nova-border-strong);
  background: var(--nova-glass-strong);
}

/* ============ Main grid (bento + feed) ============ */
.nova-home__main {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(0, 1fr);
  gap: 24px;
  margin-top: 8px;
}

.nova-bento-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-auto-rows: 200px;
  gap: 16px;
}

.nova-bento {
  position: relative;
  padding: 24px;
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.05), rgba(255, 255, 255, 0.02));
  border: 1px solid var(--nova-border);
  backdrop-filter: blur(22px) saturate(1.1);
  display: flex;
  flex-direction: column;
  gap: 14px;
  cursor: pointer;
  outline: none;
  isolation: isolate;
  overflow: hidden;
  transition: transform 0.3s cubic-bezier(0.2, 0.8, 0.2, 1),
    border-color 0.3s ease, box-shadow 0.35s ease;
}

.nova-bento:focus-visible,
.nova-bento:hover {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--card-accent) 45%, var(--nova-border-strong));
  box-shadow: 0 24px 50px -24px color-mix(in srgb, var(--card-accent) 50%, transparent);
}

.nova-bento--wide   { grid-column: span 2; }
.nova-bento--tall   { grid-column: span 1; grid-row: span 2; }
.nova-bento--normal { grid-column: span 1; }

.nova-bento__halo {
  position: absolute;
  inset: -50% -10% auto -10%;
  height: 70%;
  background: radial-gradient(60% 60% at 50% 0%, var(--card-soft), transparent 70%);
  pointer-events: none;
  z-index: -1;
  opacity: 0.85;
  transition: opacity 0.3s ease;
}

.nova-bento:hover .nova-bento__halo { opacity: 1; }

.nova-bento__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nova-bento__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: var(--card-soft);
  color: var(--card-accent);
  border: 1px solid color-mix(in srgb, var(--card-accent) 35%, transparent);
}

.nova-bento__badge {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: lowercase;
  color: var(--card-accent);
  padding: 3px 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--card-accent) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--card-accent) 35%, transparent);
}

.nova-bento__body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
}

.nova-bento__title {
  font-size: 20px;
  font-weight: 600;
  color: #fff;
  letter-spacing: -0.012em;
}

.nova-bento__desc {
  font-size: 13.5px;
  color: var(--nova-text-soft);
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ============ Feed ============ */
.nova-feed {
  position: relative;
  padding: 24px;
  border-radius: 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0.018));
  border: 1px solid var(--nova-border);
  backdrop-filter: blur(22px) saturate(1.1);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.nova-feed__title {
  font-size: 18px;
  font-weight: 600;
  color: #fff;
  letter-spacing: -0.01em;
}

.nova-feed__sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--nova-text-faded);
  line-height: 1.55;
}

.nova-feed__steps {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  counter-reset: step;
}

.nova-feed__step {
  position: relative;
  display: grid;
  grid-template-columns: 44px 1fr;
  gap: 14px;
  padding: 14px 14px 14px 12px;
  border-radius: 14px;
  border: 1px solid var(--nova-border);
  background: rgba(255, 255, 255, 0.025);
  cursor: pointer;
  transition: border-color 0.3s ease, transform 0.3s ease, background 0.3s ease;
}

.nova-feed__step:hover {
  transform: translateX(2px);
  border-color: var(--nova-border-strong);
  background: rgba(255, 255, 255, 0.045);
}

.nova-feed__step-no {
  font-size: 22px;
  font-weight: 700;
  color: var(--nova-violet);
  letter-spacing: 0.04em;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 2px;
  background: linear-gradient(120deg, var(--nova-violet) 0%, var(--nova-cyan) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.nova-feed__step-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.nova-feed__step-title {
  font-size: 14.5px;
  font-weight: 600;
  color: #fff;
  letter-spacing: -0.005em;
}

.nova-feed__step-desc {
  font-size: 12.5px;
  color: var(--nova-text-soft);
  line-height: 1.6;
}

.nova-feed__step-cta {
  margin-top: 6px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--nova-cyan);
  font-family: 'JetBrains Mono', monospace;
  text-transform: lowercase;
  transition: transform 0.3s ease;
}

.nova-feed__step:hover .nova-feed__step-cta {
  transform: translateX(2px);
}

/* ============ Responsive ============ */
@media (max-width: 1280px) {
  .nova-home__main { grid-template-columns: 1fr; }
  .nova-bento-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); grid-auto-rows: 200px; }
  .nova-bento--wide { grid-column: span 2; }
  .nova-bento--tall { grid-column: span 1; grid-row: span 2; }
}

@media (max-width: 880px) {
  .nova-home__inner { padding: 22px 18px 36px; }
  .nova-bento-grid { grid-template-columns: 1fr; grid-auto-rows: auto; }
  .nova-bento--wide,
  .nova-bento--tall,
  .nova-bento--normal { grid-column: span 1; grid-row: auto; min-height: 180px; }
}
</style>

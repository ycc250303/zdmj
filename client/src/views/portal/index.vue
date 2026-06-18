<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/auth';
import { useAppStore } from '@/store/modules/app';
import { localStg } from '@/utils/storage';
import { $t } from '@/locales';

defineOptions({
  name: 'PortalIndex'
});

const router = useRouter();
const authStore = useAuthStore();
const appStore = useAppStore();

const isLogin = computed(() => Boolean(localStg.get('token')));

const langOptions = [
  { label: '中文', key: 'zh-CN' },
  { label: 'English', key: 'en-US' }
];

function handleLangSelect(key: string | number) {
  appStore.changeLocale(key as App.I18n.LangType); 
}

function handleAction() {
  router.push(isLogin.value ? '/home' : '/login');
}

const modules = computed(() => [
  {
    key: 'agent',
    accent: '#7C5CFF',
    accentSoft: 'rgba(124, 92, 255, 0.18)',
    tag: $t('page.portal.modules.agent.tag'),
    title: $t('page.portal.modules.agent.title'),
    desc: $t('page.portal.modules.agent.desc'),
    span: 'large',
    route: '/chat'
  },
  {
    key: 'knowledge',
    accent: '#22D3EE',
    accentSoft: 'rgba(34, 211, 238, 0.18)',
    tag: $t('page.portal.modules.knowledge.tag'),
    title: $t('page.portal.modules.knowledge.title'),
    desc: $t('page.portal.modules.knowledge.desc'),
    span: 'small',
    route: '/knowledge'
  },
  {
    key: 'resume',
    accent: '#FB7185',
    accentSoft: 'rgba(251, 113, 133, 0.18)',
    tag: $t('page.portal.modules.resume.tag'),
    title: $t('page.portal.modules.resume.title'),
    desc: $t('page.portal.modules.resume.desc'),
    span: 'small',
    route: '/resume-import'
  },
  {
    key: 'role',
    accent: '#34D399',
    accentSoft: 'rgba(52, 211, 153, 0.18)',
    tag: $t('page.portal.modules.role.tag'),
    title: $t('page.portal.modules.role.title'),
    desc: $t('page.portal.modules.role.desc'),
    span: 'large',
    route: '/jobs'
  }
] as const);

function openModule(route: string) {
  if (isLogin.value) {
    router.push(route);
  } else {
    router.push('/login');
  }
}

const pillars = computed(() => {
  const isZh = appStore.locale === 'zh-CN';
  return isZh
    ? [
        { k: 'student-first', v: '面向软件工程大学生的真实求职场景设计' },
        { k: 'rag-grounded', v: '所有 AI 回答均可基于个人知识库进行引用追溯' },
        { k: 'ability-graph', v: '从简历到岗位，全流程围绕"能力画像"展开' },
        { k: 'exportable', v: '画像与简历分析结果支持一键导出 PDF' }
      ]
    : [
        { k: 'student-first', v: 'Designed for the actual job-prep workflow of CS students' },
        { k: 'rag-grounded', v: 'Every AI answer can be grounded in your private knowledge' },
        { k: 'ability-graph', v: 'A capability-profile spine connects resume, role and graph' },
        { k: 'exportable', v: 'Profiles and resume analyses can be exported to PDF' }
      ];
});

// Real tech stack from this project
const tickerItems = computed(() => {
  const items = [
    'java 21',
    'spring boot 3.5',
    'spring ai',
    'mybatis-plus',
    'postgresql · pgvector',
    'redis 7',
    'vue 3 + vite',
    'naive ui',
    'docker compose'
  ];
  return [...items, ...items];
});

</script>

<template>
  <div class="nova-portal">
    <AuroraStage variant="portal" />

    <header class="nova-header">
      <div class="nova-brand">
        <span class="nova-mark">
          <icon-local-logo class="text-26px" />
        </span>
        <span class="nova-brand__name font-display">{{ $t('page.portal.title') }}</span>
        </div>
        
      <nav class="nova-nav">
        <a class="nova-nav__item" href="#modules">{{ $t('page.portal.modules.title') }}</a>
        <a class="nova-nav__item" href="#pillars">{{ $t('page.portal.pillars.title') }}</a>
        <span class="nova-nav__sep" />
        <NDropdown :options="langOptions" placement="bottom-end" trigger="click" @select="handleLangSelect">
          <button class="nova-icon-btn" type="button">
            <icon-carbon-language class="text-18px" />
          </button>
          </NDropdown>

          <template v-if="isLogin">
          <span class="nova-user">
            <span class="nova-user__dot" />
                {{ $t('page.portal.hello') }}{{ authStore.userInfo.userName }}
              </span>
          <button class="nova-btn nova-btn--primary" type="button" @click="handleAction">
            <span>{{ $t('page.portal.enterConsole') }}</span>
            <icon-carbon-arrow-right class="text-16px" />
          </button>
          </template>
          <template v-else>
          <button class="nova-btn nova-btn--primary" type="button" @click="handleAction">
            <span>{{ $t('page.portal.loginSystem') }}</span>
            <icon-carbon-arrow-right class="text-16px" />
          </button>
          </template>
      </nav>
    </header>

    <main class="nova-main">
      <!-- ============== HERO ============== -->
      <section class="nova-hero">
        <div class="nova-hero__eyebrow">
          <span class="nova-hero__eyebrow-dot" />
          <span>{{ $t('page.portal.eyebrow') }}</span>
        </div>
        
        <h1 class="nova-hero__title font-display nova-text-gradient">
              {{ $t('page.portal.heroTitle') }}
        </h1>

        <p class="nova-hero__desc">
          {{ $t('page.portal.subhero') }}
        </p>

        <div class="nova-hero__cta">
          <button class="nova-btn nova-btn--lg nova-btn--primary" type="button" @click="handleAction">
            <span>{{ isLogin ? $t('page.portal.actionEnter') : $t('page.portal.actionLogin') }}</span>
            <icon-carbon-arrow-right class="text-18px" />
          </button>
          <a class="nova-btn nova-btn--lg nova-btn--ghost" href="#modules">
            <icon-carbon-launch class="text-18px" />
            <span>{{ $t('page.portal.modules.title') }}</span>
          </a>
        </div>
      </section>

      <!-- ============== TICKER ============== -->
      <div class="nova-ticker" aria-hidden="true">
        <div class="nova-ticker__track">
          <span v-for="(t, i) in tickerItems" :key="i" class="nova-ticker__item">
            <icon-carbon-flash class="text-14px" /> {{ t }}
          </span>
        </div>
      </div>

      <!-- ============== MODULES (BENTO) ============== -->
      <section id="modules" class="nova-section">
        <div class="nova-section__head">
          <span class="nova-eyebrow">// modules</span>
          <h2 class="nova-section__title font-display">{{ $t('page.portal.modules.title') }}</h2>
          <p class="nova-section__desc">{{ $t('page.portal.modules.subtitle') }}</p>
        </div>

        <div class="nova-bento">
          <article
            v-for="m in modules"
            :key="m.key"
            class="nova-card"
            :class="`nova-card--${m.span}`"
            :style="{ '--card-accent': m.accent, '--card-soft': m.accentSoft } as any"
            role="button"
            tabindex="0"
            @click="openModule(m.route)"
            @keyup.enter="openModule(m.route)"
          >
            <div class="nova-card__halo" />
            <div class="nova-card__top">
              <span class="nova-card__icon">
                <!-- agent / network -->
                <svg v-if="m.key === 'agent'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="2.2" />
                  <circle cx="4" cy="6" r="2" />
                  <circle cx="20" cy="6" r="2" />
                  <circle cx="4" cy="18" r="2" />
                  <circle cx="20" cy="18" r="2" />
                  <path d="M5.5 7.4L10.4 11M18.5 7.4L13.6 11M5.5 16.6L10.4 13M18.5 16.6L13.6 13" />
                </svg>
                <!-- knowledge / database -->
                <svg v-else-if="m.key === 'knowledge'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <ellipse cx="12" cy="5" rx="7" ry="2.5" />
                  <path d="M5 5v6c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5V5" />
                  <path d="M5 11v6c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5v-6" />
                </svg>
                <!-- resume / document -->
                <svg v-else-if="m.key === 'resume'" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M7 3h7l4 4v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z" />
                  <path d="M14 3v4h4" />
                  <path d="M9 12h7M9 16h7M9 8h2" />
                </svg>
                <!-- role / compass -->
                <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="9" />
                  <path d="M15.5 8.5L13 13l-4.5 2.5L11 11l4.5-2.5Z" fill="currentColor" stroke="none" />
                </svg>
              </span>
              <span class="nova-card__tag">{{ m.tag }}</span>
            </div>
            <h3 class="nova-card__title font-display">{{ m.title }}</h3>
            <p class="nova-card__desc">{{ m.desc }}</p>
            <div class="nova-card__line" />
            <span class="nova-card__cta">
              <span>{{ isLogin ? $t('page.portal.enterConsole') : $t('page.portal.loginSystem') }}</span>
              <icon-carbon-arrow-right class="text-14px" />
            </span>
          </article>
        </div>
      </section>

      <!-- ============== PILLARS ============== -->
      <section id="pillars" class="nova-section nova-section--pillars">
        <div class="nova-section__head">
          <span class="nova-eyebrow">// principles</span>
          <h2 class="nova-section__title font-display">{{ $t('page.portal.pillars.title') }}</h2>
          <p class="nova-section__desc">{{ $t('page.portal.pillars.subtitle') }}</p>
        </div>
        
        <div class="nova-pillars">
          <div v-for="(p, idx) in pillars" :key="p.k" class="nova-pillar">
            <div class="nova-pillar__index font-display">{{ String(idx + 1).padStart(2, '0') }}</div>
            <div class="nova-pillar__body">
              <div class="nova-pillar__key font-mono">{{ p.k }}</div>
              <div class="nova-pillar__value">{{ p.v }}</div>
            </div>
          </div>
        </div>
      </section>

      <!-- ============== CTA ============== -->
      <section class="nova-cta">
        <div class="nova-cta__inner">
          <h2 class="nova-cta__title font-display nova-text-gradient">
            {{ $t('page.portal.heroTitle') }}
          </h2>
          <p class="nova-cta__desc">{{ $t('page.portal.heroDesc') }}</p>
          <button class="nova-btn nova-btn--lg nova-btn--primary" type="button" @click="handleAction">
            <span>{{ isLogin ? $t('page.portal.actionEnter') : $t('page.portal.actionLogin') }}</span>
            <icon-carbon-arrow-right class="text-18px" />
          </button>
        </div>
      </section>

      <!-- ============== FOOTER ============== -->
      <footer class="nova-footer">
        <div class="nova-footer__line">
          <span class="font-display">{{ $t('page.portal.footer.tag') }}</span>
          <span class="nova-footer__sep" />
          <span>{{ $t('page.portal.footer.note') }}</span>
        </div>
      </footer>
      </main>
  </div>
</template>

<style scoped>
.nova-portal {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  color: var(--nova-text);
  background: var(--nova-bg-deep);
}

/* ====================== HEADER ====================== */
.nova-header {
  position: sticky;
  top: 0;
  z-index: 30;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 36px;
  background: linear-gradient(180deg, rgba(5, 6, 13, 0.6) 0%, rgba(5, 6, 13, 0.0) 100%);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.nova-brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.nova-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(124, 92, 255, 0.4), rgba(34, 211, 238, 0.32));
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08), 0 12px 32px -12px rgba(124, 92, 255, 0.7);
  color: #fff;
}

.nova-brand__name {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #fff;
}

.nova-nav {
  display: flex;
  align-items: center;
  gap: 18px;
}

.nova-nav__item {
  font-size: 13.5px;
  color: var(--nova-text-soft);
  cursor: pointer;
  transition: color 0.2s ease;
}

.nova-nav__item:hover {
  color: #fff;
}

.nova-nav__sep {
  width: 1px;
  height: 16px;
  background: rgba(255, 255, 255, 0.08);
}

.nova-icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid var(--nova-border);
  background: var(--nova-glass);
  color: var(--nova-text-soft);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nova-icon-btn:hover {
  color: #fff;
  border-color: var(--nova-border-strong);
  background: var(--nova-glass-strong);
}

.nova-user {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 14px;
  border-radius: 999px;
  background: var(--nova-glass);
  border: 1px solid var(--nova-border);
  font-size: 13px;
  color: var(--nova-text-soft);
}

.nova-user__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--nova-mint);
  box-shadow: 0 0 12px var(--nova-mint);
}

/* ====================== BUTTONS ====================== */
.nova-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: transform 0.2s ease, box-shadow 0.25s ease, background 0.25s ease,
    border-color 0.25s ease, color 0.25s ease;
  white-space: nowrap;
}

.nova-btn--lg {
  padding: 14px 24px;
  font-size: 15px;
  border-radius: 14px;
}

.nova-btn--primary {
  color: #fff;
  background: linear-gradient(135deg, #7c5cff 0%, #5b4cff 50%, #22d3ee 130%);
  box-shadow: 0 12px 32px -8px rgba(124, 92, 255, 0.55), inset 0 1px 0 rgba(255, 255, 255, 0.18);
}

.nova-btn--primary::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.18) 0%, transparent 60%);
  opacity: 0;
  transition: opacity 0.25s ease;
}

.nova-btn--primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 16px 38px -6px rgba(124, 92, 255, 0.65), inset 0 1px 0 rgba(255, 255, 255, 0.24);
}

.nova-btn--primary:hover::after { opacity: 1; }

.nova-btn--ghost {
  color: var(--nova-text);
  background: var(--nova-glass);
  border-color: var(--nova-border);
  backdrop-filter: blur(8px);
}

.nova-btn--ghost:hover {
  border-color: var(--nova-border-strong);
  background: var(--nova-glass-strong);
}

/* ====================== MAIN ====================== */
.nova-main {
  position: relative;
  z-index: 10;
  max-width: 1240px;
  margin: 0 auto;
  padding: 0 36px 80px;
}

/* ====================== HERO ====================== */
.nova-hero {
  position: relative;
  padding: 80px 0 64px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 26px;
  transition: transform 0.4s cubic-bezier(0.2, 0.8, 0.2, 1);
}

.nova-hero__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 7px 16px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.02));
  font-size: 12.5px;
  letter-spacing: 0.04em;
  color: var(--nova-text-soft);
  backdrop-filter: blur(8px);
}

.nova-hero__eyebrow-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--nova-violet);
  box-shadow: 0 0 12px var(--nova-violet);
  animation: nova-pulse 2.4s ease-in-out infinite;
}

@keyframes nova-pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.4); opacity: 0.55; }
}

.nova-hero__title {
  font-size: clamp(46px, 8vw, 96px);
  font-weight: 800;
  line-height: 1.02;
  white-space: pre-line;
  margin: 4px 0;
  letter-spacing: -0.022em;
}

.nova-hero__desc {
  max-width: 720px;
  font-size: clamp(15px, 1.4vw, 18px);
  line-height: 1.65;
  color: var(--nova-text-soft);
}

.nova-hero__cta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: center;
}

/* ====================== TICKER ====================== */
.nova-ticker {
  position: relative;
  margin: 36px -36px 60px;
  overflow: hidden;
  -webkit-mask-image: linear-gradient(90deg, transparent 0%, #000 12%, #000 88%, transparent 100%);
  mask-image: linear-gradient(90deg, transparent 0%, #000 12%, #000 88%, transparent 100%);
}

.nova-ticker__track {
  display: flex;
  gap: 36px;
  width: max-content;
  padding: 18px 0;
  animation: nova-marquee 38s linear infinite;
}

.nova-ticker__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  letter-spacing: 0.08em;
  color: var(--nova-text-faded);
  font-family: 'JetBrains Mono', monospace;
  text-transform: lowercase;
  white-space: nowrap;
}

.nova-ticker__item:nth-child(odd) {
  color: var(--nova-text-soft);
}

@keyframes nova-marquee {
  0%   { transform: translateX(0); }
  100% { transform: translateX(-50%); }
}

/* ====================== SECTIONS ====================== */
.nova-section {
  padding: 56px 0;
}

.nova-section__head {
  max-width: 720px;
  margin: 0 0 32px;
}

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
  margin-bottom: 14px;
}

.nova-section__title {
  font-size: clamp(30px, 4vw, 44px);
  font-weight: 700;
  color: #fff;
  letter-spacing: -0.02em;
  line-height: 1.15;
}

.nova-section__desc {
  margin-top: 12px;
  font-size: 15px;
  line-height: 1.65;
  color: var(--nova-text-soft);
}

/* ====================== BENTO ====================== */
.nova-bento {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  grid-auto-rows: 220px;
  gap: 20px;
}

.nova-card {
  position: relative;
  padding: 28px;
  border-radius: 22px;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0.02));
  border: 1px solid var(--nova-border);
  backdrop-filter: blur(22px) saturate(1.1);
  display: flex;
  flex-direction: column;
  gap: 12px;
  isolation: isolate;
  cursor: pointer;
  outline: none;
  transition: transform 0.35s cubic-bezier(0.2, 0.8, 0.2, 1),
    border-color 0.3s ease, box-shadow 0.35s ease;
}

.nova-card:focus-visible {
  border-color: color-mix(in srgb, var(--card-accent) 60%, var(--nova-border-strong));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--card-accent) 30%, transparent);
}

.nova-card--large { grid-column: span 4; }
.nova-card--small { grid-column: span 2; }

.nova-card:hover {
  transform: translateY(-4px);
  border-color: color-mix(in srgb, var(--card-accent) 45%, var(--nova-border-strong));
  box-shadow: 0 30px 60px -28px color-mix(in srgb, var(--card-accent) 50%, transparent);
}

.nova-card__halo {
  position: absolute;
  inset: -50% -10% auto -10%;
  height: 60%;
  background: radial-gradient(60% 60% at 50% 0%, var(--card-soft), transparent 70%);
  pointer-events: none;
  opacity: 0.85;
  transition: opacity 0.3s ease, transform 0.4s ease;
  z-index: -1;
}

.nova-card:hover .nova-card__halo {
  opacity: 1;
  transform: translateY(8px);
}

.nova-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nova-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--card-soft);
  color: var(--card-accent);
  border: 1px solid color-mix(in srgb, var(--card-accent) 35%, transparent);
}

.nova-card__tag {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--card-accent);
  text-transform: lowercase;
}

.nova-card__title {
  font-size: 22px;
  font-weight: 600;
  color: #fff;
  line-height: 1.25;
  letter-spacing: -0.012em;
}

.nova-card__desc {
  margin-top: 4px;
  font-size: 14px;
  color: var(--nova-text-soft);
  line-height: 1.7;
}

.nova-card__line {
  margin-top: auto;
  width: 100%;
  height: 1px;
  background: linear-gradient(90deg, transparent 0%, color-mix(in srgb, var(--card-accent) 60%, transparent) 50%, transparent 100%);
}

.nova-card__cta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  padding: 4px 0;
  font-size: 12.5px;
  letter-spacing: 0.04em;
  color: color-mix(in srgb, var(--card-accent) 80%, #fff);
  font-family: 'JetBrains Mono', monospace;
  text-transform: lowercase;
  transition: transform 0.3s ease, color 0.3s ease;
}

.nova-card:hover .nova-card__cta {
  transform: translateX(3px);
  color: #fff;
}

/* ====================== PILLARS ====================== */
.nova-section--pillars { padding-bottom: 28px; }

.nova-pillars {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  border: 1px solid var(--nova-border);
  border-radius: 22px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.018);
  backdrop-filter: blur(14px);
}

.nova-pillar {
  position: relative;
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 28px;
  border-bottom: 1px solid var(--nova-border);
}

.nova-pillar:nth-child(odd) {
  border-right: 1px solid var(--nova-border);
}
.nova-pillar:nth-last-child(-n + 2) {
  border-bottom: none;
}

.nova-pillar__index {
  font-size: 32px;
  color: rgba(255, 255, 255, 0.32);
  font-weight: 600;
  width: 56px;
  flex-shrink: 0;
}

.nova-pillar__body { flex: 1; min-width: 0; }

.nova-pillar__key {
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: lowercase;
  color: var(--nova-violet);
}

.nova-pillar__value {
  margin-top: 6px;
  font-size: 16px;
  font-weight: 500;
  color: #fff;
  line-height: 1.5;
}

/* ====================== CTA ====================== */
.nova-cta {
  margin-top: 80px;
  padding: 64px 36px;
  border-radius: 28px;
  border: 1px solid var(--nova-border);
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(70% 60% at 80% 0%, rgba(34, 211, 238, 0.25), transparent 60%),
    radial-gradient(70% 60% at 20% 100%, rgba(124, 92, 255, 0.32), transparent 60%),
    rgba(255, 255, 255, 0.025);
  backdrop-filter: blur(18px);
  text-align: center;
}

.nova-cta__inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
}

.nova-cta__title {
  font-size: clamp(28px, 4vw, 40px);
  white-space: pre-line;
  font-weight: 700;
  line-height: 1.1;
}

.nova-cta__desc {
  max-width: 560px;
  font-size: 15px;
  color: var(--nova-text-soft);
  line-height: 1.6;
}

/* ====================== FOOTER ====================== */
.nova-footer {
  margin-top: 60px;
  padding: 24px 0 8px;
  border-top: 1px solid var(--nova-border);
}

.nova-footer__line {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--nova-text-faded);
}

.nova-footer__sep {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--nova-text-faded);
}

/* ====================== RESPONSIVE ====================== */
@media (max-width: 1080px) {
  .nova-bento {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-auto-rows: auto;
  }
  .nova-card--large,
  .nova-card--small { grid-column: span 1; min-height: 200px; }
}

@media (max-width: 720px) {
  .nova-header { padding: 14px 18px; gap: 8px; }
  .nova-nav__item, .nova-nav__sep { display: none; }
  .nova-main { padding: 0 18px 48px; }
  .nova-hero { padding: 56px 0 36px; gap: 20px; }
  .nova-pillars { grid-template-columns: 1fr; }
  .nova-pillar:nth-child(odd) { border-right: none; }
  .nova-pillar:nth-last-child(-n + 2) { border-bottom: 1px solid var(--nova-border); }
  .nova-pillar:last-child { border-bottom: none; }
  .nova-cta { padding: 40px 22px; }
}
</style>

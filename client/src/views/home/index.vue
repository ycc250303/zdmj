<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { $t } from '@/locales';

const router = useRouter();

interface QuickAction {
  id: number;
  title: string;
  description: string;
  no: string;
  route: string;
  kicker: string;
}

const quickActions = computed<QuickAction[]>(() => [
  {
    id: 1,
    no: '01',
    kicker: 'Conversation',
    title: $t('page.home.aiChat.title'),
    description: $t('page.home.aiChat.description'),
    route: '/chat'
  },
  {
    id: 2,
    no: '02',
    kicker: 'Reference',
    title: $t('page.home.knowledge.title'),
    description: $t('page.home.knowledge.description'),
    route: '/knowledge'
  },
  {
    id: 3,
    no: '03',
    kicker: 'Profile',
    title: $t('page.home.resume.title'),
    description: $t('page.home.resume.description'),
    route: '/resumes'
  },
  {
    id: 4,
    no: '04',
    kicker: 'Opportunity',
    title: $t('page.home.jobs.title'),
    description: $t('page.home.jobs.description'),
    route: '/jobs'
  }
]);

function navigateTo(route: string) {
  router.push(route);
}

const today = new Date();
const dateStr = today.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' }).toUpperCase();
const weekday = today.toLocaleDateString('en-US', { weekday: 'long' }).toUpperCase();
</script>

<template>
  <div class="editorial-home">
    <div class="grain-bg paper-grain"></div>

    <!-- 报头 / 期刊头部 -->
    <header class="masthead">
      <div class="masthead-meta-l">
        <span>{{ weekday }}</span>
        <span class="dot">·</span>
        <span>{{ dateStr }}</span>
      </div>
      <div class="masthead-title font-display">{{ $t('page.home.hero.title') }}</div>
      <div class="masthead-meta-r">
        <span>VOL. 02</span>
        <span class="dot">·</span>
        <span>EDITORIAL</span>
      </div>
    </header>
    <div class="masthead-rule double"></div>

    <!-- Hero / Cover Story -->
    <section class="cover-story">
      <div class="cover-eyebrow">
        <span class="eyebrow-bar"></span>
        <span class="eyebrow-tag">{{ $t('page.home.hero.badge') }}</span>
      </div>
      <h1 class="cover-headline font-display">{{ $t('page.home.hero.subtitle') }}</h1>
      <p class="cover-byline">— Today's selected stories, curated for you.</p>
    </section>

    <!-- 目录式四栏 -->
    <section class="features">
      <div class="features-label font-display">In this section</div>
      <div class="feature-grid">
        <article
          v-for="action in quickActions"
          :key="action.id"
          class="feature-item"
          @click="navigateTo(action.route)"
        >
          <div class="feature-no font-display">{{ action.no }}</div>
          <div class="feature-body">
            <div class="feature-kicker">— {{ action.kicker }}</div>
            <h3 class="feature-title font-display">{{ action.title }}</h3>
            <p class="feature-desc">{{ action.description }}</p>
            <span class="feature-link">
              Read more <span class="arrow">→</span>
            </span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.editorial-home {
  position: relative;
  min-height: 100%;
  height: 100%;
  padding: 40px 56px 56px;
  background: var(--brand-cream);
  color: var(--brand-ink);
  overflow: auto;
}

.grain-bg {
  position: absolute;
  inset: 0;
  opacity: 0.4;
  pointer-events: none;
}

/* ============== Masthead ============== */
.masthead {
  position: relative;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: end;
  gap: 24px;
  padding-bottom: 14px;
}

.masthead-meta-l,
.masthead-meta-r {
  font-size: 11px;
  letter-spacing: 0.18em;
  color: var(--brand-ink-soft);
  display: flex;
  align-items: center;
  gap: 8px;
}
.masthead-meta-r {
  justify-content: flex-end;
}

.masthead-meta-l .dot,
.masthead-meta-r .dot {
  opacity: 0.5;
}

.masthead-title {
  font-size: clamp(36px, 4vw, 56px);
  font-weight: 700;
  letter-spacing: -0.02em;
  text-align: center;
  line-height: 1;
}

.masthead-rule {
  height: 1px;
  background: var(--brand-line);
  margin-bottom: 56px;
  position: relative;
}
.masthead-rule.double::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 4px;
  height: 1px;
  background: var(--brand-line);
}

/* ============== Cover Story ============== */
.cover-story {
  position: relative;
  z-index: 1;
  max-width: 880px;
  margin: 0 auto 72px;
  text-align: center;
}

.cover-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.eyebrow-bar {
  width: 40px;
  height: 2px;
  background: var(--brand-mocha);
}

.eyebrow-tag {
  font-size: 12px;
  letter-spacing: 0.22em;
  color: var(--brand-mocha-deep);
}

:global(.dark) .eyebrow-tag {
  color: #e8b496;
}

.cover-headline {
  font-size: clamp(40px, 5.5vw, 80px);
  line-height: 1.05;
  font-weight: 600;
  letter-spacing: -0.025em;
  color: var(--brand-ink);
  margin-bottom: 20px;
}

.cover-byline {
  font-family: var(--serif-display);
  font-style: italic;
  font-size: 17px;
  color: var(--brand-ink-soft);
}

/* ============== Features (目录式) ============== */
.features {
  position: relative;
  z-index: 1;
  max-width: 1280px;
  margin: 0 auto;
}

.features-label {
  font-style: italic;
  font-size: 16px;
  color: var(--brand-ink-soft);
  border-top: 1px solid var(--brand-line);
  padding-top: 18px;
  margin-bottom: 24px;
  letter-spacing: 0.02em;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
}

.feature-item {
  position: relative;
  cursor: pointer;
  padding: 32px 24px 32px;
  border-left: 1px solid var(--brand-line);
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.feature-item:first-child {
  border-left: none;
}

.feature-item:hover {
  background: rgba(184, 107, 75, 0.04);
}

.feature-no {
  font-size: 18px;
  font-style: italic;
  color: var(--brand-mocha);
  margin-bottom: 18px;
  letter-spacing: 0.02em;
}

.feature-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feature-kicker {
  font-size: 11px;
  letter-spacing: 0.2em;
  color: var(--brand-ink-soft);
}

.feature-title {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: -0.01em;
  color: var(--brand-ink);
}

.feature-desc {
  font-size: 14px;
  line-height: 1.7;
  color: var(--brand-ink-soft);
  margin: 4px 0 12px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.feature-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--serif-display);
  font-style: italic;
  font-size: 14px;
  color: var(--brand-mocha-deep);
  margin-top: 4px;
}

.feature-link .arrow {
  transition: transform 0.3s ease;
}

.feature-item:hover .feature-link .arrow {
  transform: translateX(6px);
}

/* 响应 */
@media (max-width: 1100px) {
  .feature-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .feature-item:nth-child(3) {
    border-left: none;
  }
  .feature-item:nth-child(n+3) {
    border-top: 1px solid var(--brand-line);
    margin-top: -1px;
  }
}

@media (max-width: 640px) {
  .editorial-home {
    padding: 24px 20px 32px;
  }
  .masthead {
    grid-template-columns: 1fr;
    text-align: center;
  }
  .masthead-meta-l,
  .masthead-meta-r {
    justify-content: center;
  }
  .feature-grid {
    grid-template-columns: 1fr;
  }
  .feature-item {
    border-left: none !important;
    border-top: 1px solid var(--brand-line) !important;
    margin-top: -1px;
  }
  .feature-item:first-child {
    border-top: none !important;
    margin-top: 0;
  }
}
</style>

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
  if (isLogin.value) {
    router.push('/home');
  } else {
    router.push('/login');
  }
}
</script>

<template>
  <div class="editorial-portal">
    <!-- 顶部导航栏（极简、纸张感） -->
    <header class="portal-header">
      <div class="header-inner">
        <div class="brand-mark">
          <span class="brand-symbol font-display">Z</span>
          <span class="brand-text font-display">{{ $t('page.portal.title') }}</span>
        </div>

        <nav class="header-meta">
          <span class="meta-item">VOL. 02</span>
          <span class="meta-divider">/</span>
          <span class="meta-item">2026</span>
          <span class="meta-divider">/</span>
          <span class="meta-item">EDITORIAL</span>
        </nav>

        <div class="header-actions">
          <NDropdown :options="langOptions" placement="bottom" @select="handleLangSelect">
            <button class="ghost-btn">
              <icon-carbon-language class="text-icon" />
            </button>
          </NDropdown>

          <template v-if="isLogin">
            <span class="user-greet font-display">
              {{ $t('page.portal.hello') }}{{ authStore.userInfo.userName }}
            </span>
            <button class="primary-btn" @click="handleAction">
              {{ $t('page.portal.enterConsole') }}
              <span class="arrow">→</span>
            </button>
          </template>
          <template v-else>
            <button class="primary-btn" @click="handleAction">
              {{ $t('page.portal.loginSystem') }}
              <span class="arrow">→</span>
            </button>
          </template>
        </div>
      </div>
      <div class="header-rule"></div>
    </header>

    <!-- 主体：杂志式版式 -->
    <main class="portal-main">
      <div class="grain-bg paper-grain"></div>

      <section class="hero-grid">
        <!-- 左侧：标题 -->
        <div class="hero-text">
          <div class="hero-issue">
            <span class="issue-bar"></span>
            <span class="issue-tag">— THE COVER FEATURE —</span>
          </div>

          <h1 class="hero-title font-display">
            {{ $t('page.portal.heroTitle') }}
          </h1>

          <p class="hero-desc">{{ $t('page.portal.heroDesc') }}</p>

          <div class="hero-cta">
            <button class="primary-btn lg" @click="handleAction">
              {{ isLogin ? $t('page.portal.actionEnter') : $t('page.portal.actionLogin') }}
              <span class="arrow">→</span>
            </button>
            <span class="cta-note">no.1 — start your story</span>
          </div>
        </div>

        <!-- 右侧：装饰风格化卡片 -->
        <div class="hero-side">
          <div class="poster">
            <div class="poster-no font-display">N°<br /><span>2026</span></div>
            <div class="poster-circle"></div>
            <div class="poster-line"></div>
            <div class="poster-tag">— FEATURED —</div>
            <div class="poster-quote font-display">
              “Quietly<br /><em>powerful</em>.”
            </div>
          </div>
        </div>
      </section>

      <!-- 底部目录条（杂志感） -->
      <section class="contents-strip">
        <div class="strip-label font-display">In this issue</div>
        <div class="strip-items">
          <div class="strip-item">
            <span class="strip-no">01</span>
            <span class="strip-name">AI Chat</span>
          </div>
          <div class="strip-item">
            <span class="strip-no">02</span>
            <span class="strip-name">Knowledge</span>
          </div>
          <div class="strip-item">
            <span class="strip-no">03</span>
            <span class="strip-name">Resume</span>
          </div>
          <div class="strip-item">
            <span class="strip-no">04</span>
            <span class="strip-name">Jobs</span>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.editorial-portal {
  position: relative;
  min-height: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--brand-cream);
  color: var(--brand-ink);
  overflow: auto;
}

/* ============== Header ============== */
.portal-header {
  background: var(--brand-paper);
}

.header-inner {
  max-width: 1280px;
  margin: 0 auto;
  height: 72px;
  padding: 0 40px;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 32px;
}

.brand-mark {
  display: inline-flex;
  align-items: baseline;
  gap: 10px;
}

.brand-symbol {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--brand-mocha);
  color: var(--brand-paper);
  font-size: 22px;
  font-weight: 700;
  border-radius: 50%;
  font-style: italic;
}

.brand-text {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.header-meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--brand-ink-soft);
}

.meta-divider {
  opacity: 0.5;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.user-greet {
  font-size: 14px;
  font-style: italic;
  color: var(--brand-ink-soft);
}

.header-rule {
  height: 1px;
  background: var(--brand-line);
  max-width: 1280px;
  margin: 0 auto;
}

/* 共用按钮 */
.ghost-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--brand-line);
  background: transparent;
  color: var(--brand-ink-soft);
  cursor: pointer;
  transition: all 0.25s ease;
}
.ghost-btn:hover {
  border-color: var(--brand-mocha);
  color: var(--brand-mocha);
}

.primary-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 20px;
  border-radius: 999px;
  background: var(--brand-ink);
  color: var(--brand-paper);
  border: none;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  letter-spacing: 0.02em;
  transition: all 0.3s ease;
}
.primary-btn:hover {
  background: var(--brand-mocha-deep);
  transform: translateY(-1px);
}
.primary-btn .arrow {
  transition: transform 0.3s ease;
}
.primary-btn:hover .arrow {
  transform: translateX(3px);
}
.primary-btn.lg {
  padding: 14px 28px;
  font-size: 15px;
}

/* ============== Main ============== */
.portal-main {
  position: relative;
  flex: 1;
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
  padding: 64px 40px 56px;
  display: flex;
  flex-direction: column;
  gap: 64px;
}

.grain-bg {
  position: absolute;
  inset: 0;
  opacity: 0.4;
  pointer-events: none;
}

/* Hero grid */
.hero-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 64px;
  align-items: center;
}

.hero-issue {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}
.issue-bar {
  width: 60px;
  height: 2px;
  background: var(--brand-mocha);
}
.issue-tag {
  font-size: 12px;
  letter-spacing: 0.2em;
  color: var(--brand-ink-soft);
}

.hero-title {
  font-size: clamp(56px, 7vw, 104px);
  line-height: 0.98;
  font-weight: 600;
  letter-spacing: -0.03em;
  color: var(--brand-ink);
  white-space: pre-line;
  margin-bottom: 28px;
}

.hero-desc {
  font-size: 17px;
  line-height: 1.75;
  color: var(--brand-ink-soft);
  max-width: 540px;
  margin-bottom: 36px;
  white-space: pre-line;
}

.hero-cta {
  display: flex;
  align-items: center;
  gap: 24px;
}
.cta-note {
  font-family: var(--serif-display);
  font-style: italic;
  font-size: 14px;
  color: var(--brand-ink-soft);
}

/* Right poster */
.hero-side {
  position: relative;
  height: 480px;
}
.poster {
  position: absolute;
  inset: 0;
  background: linear-gradient(170deg, #efe4d3 0%, #d9bfa0 100%);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--brand-shadow-lg);
  padding: 32px;
}

:global(.dark) .poster {
  background: linear-gradient(170deg, #2a2017 0%, #14100c 100%);
}

.poster-no {
  position: absolute;
  top: 28px;
  left: 28px;
  font-size: 22px;
  line-height: 1;
  color: var(--brand-mocha-deep);
  font-style: italic;
}
.poster-no span {
  font-size: 56px;
  display: block;
  margin-top: 4px;
  letter-spacing: -0.04em;
}

:global(.dark) .poster-no {
  color: #e8b496;
}

.poster-circle {
  position: absolute;
  right: -80px;
  top: -80px;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, var(--brand-mocha), var(--brand-mocha-deep) 70%);
  opacity: 0.85;
}

.poster-line {
  position: absolute;
  left: 28px;
  right: 28px;
  bottom: 130px;
  height: 1px;
  background: rgba(45, 33, 26, 0.3);
}

:global(.dark) .poster-line {
  background: rgba(232, 200, 170, 0.2);
}

.poster-tag {
  position: absolute;
  left: 28px;
  bottom: 100px;
  font-size: 11px;
  letter-spacing: 0.2em;
  color: var(--brand-ink-soft);
}

.poster-quote {
  position: absolute;
  left: 28px;
  right: 28px;
  bottom: 28px;
  font-size: 38px;
  line-height: 1.05;
  font-weight: 600;
  color: var(--brand-ink);
  letter-spacing: -0.02em;
}
.poster-quote em {
  font-style: italic;
  color: var(--brand-mocha-deep);
}
:global(.dark) .poster-quote em {
  color: #e8b496;
}

/* Contents strip */
.contents-strip {
  position: relative;
  z-index: 1;
  border-top: 1px solid var(--brand-line);
  border-bottom: 1px solid var(--brand-line);
  padding: 28px 0;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 56px;
  align-items: center;
}

.strip-label {
  font-size: 18px;
  font-style: italic;
  color: var(--brand-ink-soft);
}

.strip-items {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.strip-item {
  display: flex;
  align-items: baseline;
  gap: 10px;
  border-left: 1px solid var(--brand-line);
  padding-left: 16px;
}

.strip-no {
  font-family: var(--serif-display);
  font-size: 14px;
  color: var(--brand-mocha);
  letter-spacing: 0.04em;
}

.strip-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--brand-ink);
}

/* 响应 */
@media (max-width: 960px) {
  .hero-grid {
    grid-template-columns: 1fr;
    gap: 40px;
  }
  .hero-side {
    height: 320px;
  }
  .header-meta {
    display: none;
  }
  .contents-strip {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .strip-items {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

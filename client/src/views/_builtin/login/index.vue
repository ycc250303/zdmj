<script setup lang="ts">
import { computed } from 'vue';
import type { Component } from 'vue';
import { loginModuleRecord } from '@/constants/app';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { $t } from '@/locales';
import PwdLogin from './modules/pwd-login.vue';
import CodeLogin from './modules/code-login.vue';
import Register from './modules/register.vue';
import ResetPwd from './modules/reset-pwd.vue';
import BindWechat from './modules/bind-wechat.vue';

interface Props {
  /** The login module */
  module?: UnionKey.LoginModule;
}

const props = defineProps<Props>();

const appStore = useAppStore();
const themeStore = useThemeStore();

interface LoginModule {
  label: App.I18n.I18nKey;
  component: Component;
}

const moduleMap: Record<UnionKey.LoginModule, LoginModule> = {
  'pwd-login': { label: loginModuleRecord['pwd-login'], component: PwdLogin },
  'code-login': { label: loginModuleRecord['code-login'], component: CodeLogin },
  register: { label: loginModuleRecord.register, component: Register },
  'reset-pwd': { label: loginModuleRecord['reset-pwd'], component: ResetPwd },
  'bind-wechat': { label: loginModuleRecord['bind-wechat'], component: BindWechat }
};

const activeModule = computed(() => moduleMap[props.module || 'pwd-login']);
</script>

<template>
  <div class="editorial-login">
    <!-- 左侧：叙事品牌区 -->
    <aside class="brand-side">
      <div class="brand-grain paper-grain"></div>

      <div class="brand-top">
        <div class="brand-mark">
          <SystemLogo class="text-32px" />
          <span class="brand-name font-display">{{ $t('system.title') }}</span>
        </div>
      </div>

      <div class="brand-quote">
        <span class="quote-mark">“</span>
        <h1 class="font-display brand-headline">
          人才与机会的<br />
          <em>温度感</em> 链接。
        </h1>
        <p class="brand-sub">
          A quieter, more human way to discover, evaluate and grow careers — built around editorial clarity.
        </p>
      </div>

      <div class="brand-foot">
        <div class="foot-issue">
          <span class="issue-label">VOL.</span>
          <span class="issue-no font-display">02 / 2026</span>
        </div>
        <div class="foot-tag">— EDITORIAL EDITION —</div>
      </div>
    </aside>

    <!-- 右侧：表单区 -->
    <main class="form-side">
      <div class="form-top">
        <ThemeSchemaSwitch
          :theme-schema="themeStore.themeScheme"
          :show-tooltip="false"
          class="text-18px op-70 hover:op-100"
          @switch="themeStore.toggleThemeScheme"
        />
        <LangSwitch
          v-if="themeStore.header.multilingual.visible"
          :lang="appStore.locale"
          :lang-options="appStore.localeOptions"
          :show-tooltip="false"
          class="op-70 hover:op-100"
          @change-lang="appStore.changeLocale"
        />
      </div>

      <div class="form-shell">
        <div class="form-eyebrow brand-pill">
          <span class="dot"></span>
          {{ $t(activeModule.label) }}
        </div>

        <h2 class="form-title font-display">
          欢迎回来，<br />
          <span class="title-accent">让我们继续。</span>
        </h2>
        <p class="form-desc">输入信息以进入系统。每一次访问都被精心记录，如同一篇连载。</p>

        <div class="form-divider"><span></span></div>

        <Transition :name="themeStore.page.animateMode" mode="out-in" appear>
          <component :is="activeModule.component" />
        </Transition>

        <p class="form-foot">
          © {{ new Date().getFullYear() }} {{ $t('system.title') }} — Crafted with care.
        </p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.editorial-login {
  position: relative;
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  background: var(--brand-cream);
  color: var(--brand-ink);
  overflow: hidden;
}

/* ============== 左侧 ============== */
.brand-side {
  position: relative;
  padding: 48px 56px;
  background:
    radial-gradient(circle at 15% 18%, rgba(184, 107, 75, 0.18), transparent 55%),
    radial-gradient(circle at 80% 70%, rgba(63, 138, 106, 0.14), transparent 55%),
    linear-gradient(160deg, #efe4d3 0%, #e6d4bb 60%, #d9bfa0 100%);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}

:global(.dark) .brand-side {
  background:
    radial-gradient(circle at 15% 18%, rgba(184, 107, 75, 0.32), transparent 55%),
    radial-gradient(circle at 80% 70%, rgba(63, 138, 106, 0.22), transparent 55%),
    linear-gradient(160deg, #2a2017 0%, #1f1812 60%, #14100c 100%);
}

.brand-grain {
  position: absolute;
  inset: 0;
  opacity: 0.6;
  pointer-events: none;
  mix-blend-mode: multiply;
}

:global(.dark) .brand-grain {
  mix-blend-mode: screen;
  opacity: 0.3;
}

.brand-top {
  position: relative;
  z-index: 2;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  gap: 12px;
}

.brand-mark :deep(svg) {
  color: var(--brand-mocha-deep);
}

:global(.dark) .brand-mark :deep(svg) {
  color: #e8b496;
}

.brand-name {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--brand-mocha-deep);
}

:global(.dark) .brand-name {
  color: #f0d4bc;
}

.brand-quote {
  position: relative;
  z-index: 2;
  max-width: 520px;
}

.quote-mark {
  font-family: var(--serif-display);
  font-size: 140px;
  line-height: 1;
  color: var(--brand-mocha);
  opacity: 0.35;
  display: block;
  margin-bottom: -40px;
  margin-left: -8px;
}

.brand-headline {
  font-size: clamp(40px, 4.6vw, 64px);
  line-height: 1.05;
  font-weight: 600;
  color: var(--brand-ink);
  margin-bottom: 24px;
  letter-spacing: -0.02em;
}

.brand-headline em {
  font-style: italic;
  color: var(--brand-mocha-deep);
  font-weight: 500;
  position: relative;
}

.brand-headline em::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 4px;
  height: 10px;
  background: rgba(215, 161, 59, 0.4);
  z-index: -1;
  border-radius: 2px;
}

.brand-sub {
  font-size: 15px;
  line-height: 1.7;
  color: var(--brand-ink-soft);
  max-width: 440px;
  font-style: italic;
}

.brand-foot {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  align-items: end;
  font-size: 12px;
  color: var(--brand-ink-soft);
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.foot-issue {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.issue-label {
  font-size: 11px;
}

.issue-no {
  font-size: 28px;
  letter-spacing: 0.04em;
  color: var(--brand-ink);
}

.foot-tag {
  font-size: 11px;
}

/* ============== 右侧 ============== */
.form-side {
  position: relative;
  padding: 32px 56px;
  display: flex;
  flex-direction: column;
  background: var(--brand-paper);
}

.form-top {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 20px;
  height: 32px;
}

.form-shell {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: 420px;
  width: 100%;
  margin: 0 auto;
  padding: 32px 0;
}

.form-eyebrow {
  align-self: flex-start;
  margin-bottom: 28px;
}

.form-eyebrow .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand-mocha);
}

.form-title {
  font-size: 44px;
  line-height: 1.1;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--brand-ink);
  margin-bottom: 14px;
}

.title-accent {
  font-style: italic;
  color: var(--brand-mocha-deep);
  font-weight: 500;
}

:global(.dark) .title-accent {
  color: #e8b496;
}

.form-desc {
  font-size: 14.5px;
  color: var(--brand-ink-soft);
  line-height: 1.7;
  margin-bottom: 28px;
}

.form-divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 28px;
}

.form-divider::before,
.form-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--brand-line);
}

.form-divider span::before {
  content: '✦';
  color: var(--brand-mocha);
  font-size: 14px;
}

.form-foot {
  margin-top: 36px;
  font-size: 12px;
  color: var(--brand-ink-soft);
  letter-spacing: 0.04em;
  text-align: center;
  opacity: 0.7;
}

/* 响应式：小屏只显示右侧表单 */
@media (max-width: 960px) {
  .editorial-login {
    grid-template-columns: 1fr;
  }
  .brand-side {
    display: none;
  }
  .form-side {
    padding: 24px;
  }
}
</style>

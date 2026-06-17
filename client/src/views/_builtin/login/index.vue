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

const featureLines = computed(() => {
  const isZh = appStore.locale === 'zh-CN';
  return isZh
    ? [
        { k: 'resume',    v: 'PDF 简历自动结构化解析，生成学生能力画像' },
        { k: 'knowledge', v: '上传学习资料构建私有知识库，向量检索召回' },
        { k: 'chat',      v: '与 AI 流式对话，可启用 RAG 引用知识库内容' },
        { k: 'role',      v: '岗位 JD 解构、岗位画像生成与人岗匹配分析' }
      ]
    : [
        { k: 'resume',    v: 'Auto-parse a PDF resume and build a student capability profile' },
        { k: 'knowledge', v: 'Upload study materials into a private vector knowledge base' },
        { k: 'chat',      v: 'Streaming AI chat with optional RAG citations' },
        { k: 'role',      v: 'Decompose a JD, generate role profile and run matching analysis' }
      ];
});
</script>

<template>
  <div class="nova-auth">
    <AuroraStage variant="auth" />

    <!-- ===== left: brand showcase ===== -->
    <aside class="nova-auth__showcase">
      <RouterLink to="/" class="nova-auth__brand">
        <span class="nova-auth__brand-mark">
          <icon-local-logo class="text-26px" />
        </span>
        <span class="nova-auth__brand-name font-display">{{ $t('system.title') }}</span>
      </RouterLink>

      <div class="nova-auth__hero">
        <span class="nova-auth__eyebrow">
          <span class="nova-auth__eyebrow-dot" />
          {{ appStore.locale === 'zh-CN' ? '面向软件工程大学生 · 智能求职导航' : 'For software engineering students · Career navigation' }}
        </span>
        <h1 class="nova-auth__hero-title font-display nova-text-gradient">
          {{ $t('page.portal.heroTitle') }}
        </h1>
        <p class="nova-auth__hero-desc">{{ $t('page.portal.heroDesc') }}</p>
      </div>

      <ul class="nova-auth__features">
        <li v-for="f in featureLines" :key="f.k" class="nova-auth__feature">
          <span class="nova-auth__feature-key font-mono">{{ f.k }}</span>
          <span class="nova-auth__feature-value">{{ f.v }}</span>
        </li>
      </ul>

      <div class="nova-auth__sig font-mono">
        <span class="nova-auth__sig-line" />
        <span>{{ appStore.locale === 'zh-CN' ? '"职"点迷津 / 求职导航工作台' : '"ZhiDian" / career navigation workspace' }}</span>
      </div>
    </aside>

    <!-- ===== right: login glass card ===== -->
    <section class="nova-auth__panel">
      <div class="nova-auth__panel-top">
        <RouterLink to="/" class="nova-auth__back">
          <icon-carbon-arrow-left class="text-16px" />
          <span>{{ appStore.locale === 'zh-CN' ? '返回官网' : 'Back to home' }}</span>
        </RouterLink>
        <div class="nova-auth__top-actions">
          <ThemeSchemaSwitch
            :theme-schema="themeStore.themeScheme"
            :show-tooltip="false"
            class="nova-auth__top-btn"
            @switch="themeStore.toggleThemeScheme"
          />
          <LangSwitch
            v-if="themeStore.header.multilingual.visible"
            :lang="appStore.locale"
            :lang-options="appStore.localeOptions"
            :show-tooltip="false"
            class="nova-auth__top-btn"
            @change-lang="appStore.changeLocale"
          />
        </div>
      </div>

      <div class="nova-auth__card">
        <div class="nova-auth__card-glow" />
        <header class="nova-auth__card-head">
          <h2 class="nova-auth__card-title font-display">{{ $t(activeModule.label) }}</h2>
          <p class="nova-auth__card-sub">
            {{
              appStore.locale === 'zh-CN'
                ? '使用账号密码进入工作台，或选择验证码方式继续。'
                : 'Sign in with credentials, or use a verification code to continue.'
            }}
          </p>
        </header>

        <div class="nova-auth__card-body">
          <Transition :name="themeStore.page.animateMode" mode="out-in" appear>
            <component :is="activeModule.component" />
          </Transition>
        </div>

        <footer class="nova-auth__card-foot font-mono">
          <span>secure · streaming · rag-grounded</span>
        </footer>
      </div>
    </section>
  </div>
</template>

<style scoped>
.nova-auth {
  position: relative;
  min-height: 100vh;
  background: var(--nova-bg-deep);
  color: var(--nova-text);
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  overflow: hidden;
}

/* =========== showcase (left) =========== */
.nova-auth__showcase {
  position: relative;
  z-index: 5;
  padding: 56px 64px;
  display: flex;
  flex-direction: column;
  gap: 36px;
  justify-content: space-between;
  border-right: 1px solid var(--nova-border);
  background:
    radial-gradient(80% 60% at 0% 0%, rgba(124, 92, 255, 0.18), transparent 60%),
    radial-gradient(80% 60% at 0% 100%, rgba(34, 211, 238, 0.10), transparent 60%);
}

.nova-auth__brand {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  width: fit-content;
}

.nova-auth__brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: linear-gradient(135deg, rgba(124, 92, 255, 0.4), rgba(34, 211, 238, 0.32));
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08), 0 12px 28px -10px rgba(124, 92, 255, 0.7);
}

.nova-auth__brand-name {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #fff;
}

.nova-auth__hero {
  max-width: 520px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.nova-auth__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 6px 14px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.04);
  font-size: 12px;
  letter-spacing: 0.04em;
  color: var(--nova-text-soft);
  width: fit-content;
}

.nova-auth__eyebrow-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--nova-mint);
  box-shadow: 0 0 12px var(--nova-mint);
  animation: nova-pulse 2.4s ease-in-out infinite;
}

@keyframes nova-pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.4); opacity: 0.55; }
}

.nova-auth__hero-title {
  font-size: clamp(40px, 5vw, 64px);
  font-weight: 800;
  line-height: 1.05;
  white-space: pre-line;
  letter-spacing: -0.018em;
}

.nova-auth__hero-desc {
  font-size: 15px;
  line-height: 1.7;
  color: var(--nova-text-soft);
}

.nova-auth__features {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: 480px;
}

.nova-auth__feature {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 18px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0.018));
  border: 1px solid var(--nova-border);
  backdrop-filter: blur(14px);
  font-size: 14px;
}

.nova-auth__feature-key {
  flex-shrink: 0;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  letter-spacing: 0.16em;
  text-transform: lowercase;
  color: var(--nova-violet);
  background: rgba(124, 92, 255, 0.12);
  border: 1px solid rgba(124, 92, 255, 0.28);
}

.nova-auth__feature-value {
  color: var(--nova-text);
  line-height: 1.55;
}

.nova-auth__sig {
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--nova-text-faded);
  font-size: 11.5px;
  letter-spacing: 0.16em;
  text-transform: lowercase;
}

.nova-auth__sig-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, var(--nova-border), transparent);
}

/* =========== panel (right) =========== */
.nova-auth__panel {
  position: relative;
  z-index: 5;
  padding: 36px 56px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: space-between;
  gap: 24px;
}

.nova-auth__panel-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nova-auth__back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 10px;
  border: 1px solid var(--nova-border);
  background: var(--nova-glass);
  color: var(--nova-text-soft);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nova-auth__back:hover {
  color: #fff;
  border-color: var(--nova-border-strong);
  background: var(--nova-glass-strong);
}

.nova-auth__top-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nova-auth__top-btn {
  font-size: 18px;
  color: var(--nova-text-soft);
}

/* glass card */
.nova-auth__card {
  position: relative;
  margin: auto 0;
  align-self: center;
  width: 100%;
  max-width: 460px;
  padding: 40px 36px 32px;
  border-radius: 24px;
  border: 1px solid var(--nova-border-strong);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.05), rgba(255, 255, 255, 0.02));
  backdrop-filter: blur(28px) saturate(1.2);
  -webkit-backdrop-filter: blur(28px) saturate(1.2);
  box-shadow: 0 30px 80px -28px rgba(15, 18, 40, 0.6),
    0 12px 32px -12px rgba(124, 92, 255, 0.45),
    inset 0 1px 0 rgba(255, 255, 255, 0.05);
  overflow: hidden;
  isolation: isolate;
}

.nova-auth__card-glow {
  position: absolute;
  inset: -50% -10% auto -10%;
  height: 80%;
  background: radial-gradient(60% 60% at 50% 0%, rgba(124, 92, 255, 0.32), transparent 65%);
  pointer-events: none;
  z-index: -1;
}

.nova-auth__card-head { margin-bottom: 26px; }

.nova-auth__card-title {
  font-size: 24px;
  font-weight: 700;
  color: #fff;
  letter-spacing: -0.012em;
}

.nova-auth__card-sub {
  margin-top: 6px;
  font-size: 13.5px;
  color: var(--nova-text-soft);
  line-height: 1.65;
}

.nova-auth__card-body :deep(.n-input) {
  --n-color: rgba(255, 255, 255, 0.04);
  --n-color-focus: rgba(255, 255, 255, 0.06);
  --n-border: 1px solid rgba(255, 255, 255, 0.10);
  --n-border-hover: 1px solid rgba(255, 255, 255, 0.22);
  --n-border-focus: 1px solid rgba(124, 92, 255, 0.55);
  --n-text-color: #fff;
  --n-placeholder-color: rgba(255, 255, 255, 0.4);
  --n-icon-color: rgba(255, 255, 255, 0.45);
  --n-box-shadow-focus: 0 0 0 2px rgba(124, 92, 255, 0.18);
  border-radius: 12px;
}

.nova-auth__card-body :deep(.n-input .n-input__input-el),
.nova-auth__card-body :deep(.n-input .n-input__textarea-el) {
  color: #fff;
}

.nova-auth__card-body :deep(.n-checkbox) {
  --n-text-color: var(--nova-text-soft);
}

.nova-auth__card-body :deep(.n-button:not(.n-button--primary-type)) {
  --n-color: rgba(255, 255, 255, 0.04);
  --n-color-hover: rgba(255, 255, 255, 0.08);
  --n-text-color: var(--nova-text-soft);
  --n-text-color-hover: #fff;
  --n-border: 1px solid rgba(255, 255, 255, 0.10);
  --n-border-hover: 1px solid rgba(255, 255, 255, 0.22);
}

.nova-auth__card-body :deep(.n-button--primary-type) {
  --n-color: linear-gradient(135deg, #7C5CFF 0%, #5B4CFF 50%, #22D3EE 130%);
  --n-color-hover: linear-gradient(135deg, #8C6CFF 0%, #6B5CFF 50%, #34E3FF 130%);
  --n-color-pressed: linear-gradient(135deg, #6B4CEF 0%, #4B3CEF 50%, #12C3DE 130%);
  --n-text-color: #fff;
  --n-text-color-hover: #fff;
  --n-border: none;
  --n-border-hover: none;
  --n-border-pressed: none;
  --n-border-focus: none;
  box-shadow: 0 12px 32px -8px rgba(124, 92, 255, 0.55), inset 0 1px 0 rgba(255, 255, 255, 0.18);
  font-weight: 600;
}

.nova-auth__card-foot {
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px dashed var(--nova-border);
  font-size: 11px;
  color: var(--nova-text-faded);
  letter-spacing: 0.16em;
  text-align: center;
  text-transform: lowercase;
}

/* =========== responsive =========== */
@media (max-width: 960px) {
  .nova-auth { grid-template-columns: 1fr; }
  .nova-auth__showcase {
    border-right: none;
    border-bottom: 1px solid var(--nova-border);
    padding: 36px 28px;
    gap: 22px;
  }
  .nova-auth__features { display: none; }
  .nova-auth__hero-title { font-size: clamp(32px, 8vw, 44px); }
  .nova-auth__panel { padding: 24px 22px 36px; }
}
</style>

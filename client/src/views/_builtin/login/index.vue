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

interface Props { module?: UnionKey.LoginModule; }
const props = defineProps<Props>();

const appStore = useAppStore();
const themeStore = useThemeStore();

const moduleMap: Record<UnionKey.LoginModule, { label: App.I18n.I18nKey; component: Component }> = {
  'pwd-login': { label: loginModuleRecord['pwd-login'], component: PwdLogin },
  'code-login': { label: loginModuleRecord['code-login'], component: CodeLogin },
  register: { label: loginModuleRecord.register, component: Register },
  'reset-pwd': { label: loginModuleRecord['reset-pwd'], component: ResetPwd },
  'bind-wechat': { label: loginModuleRecord['bind-wechat'], component: BindWechat }
};

const activeModule = computed(() => moduleMap[props.module || 'pwd-login']);
</script>

<template>
  <div class="login-root">
    <!-- Left: Brand -->
    <aside class="login-brand">
      <div class="brand-inner">
        <div class="brand-top">
          <SystemLogo class="text-28px" />
          <span class="brand-app">{{ $t('system.title') }}</span>
        </div>

        <div class="brand-hero">
          <h1 class="brand-heading">人才与机会的<span class="brand-accent">温度感</span>链接。</h1>
          <p class="brand-desc">A more human way to discover, evaluate and grow careers — built around clarity.</p>
        </div>

        <div class="brand-foot">
          <span class="brand-version">v2.0 · 2026</span>
        </div>

        <!-- Subtle animated gradient orbs -->
        <div class="brand-orb brand-orb-1"></div>
        <div class="brand-orb brand-orb-2"></div>
      </div>
    </aside>

    <!-- Right: Form -->
    <main class="login-form">
      <div class="form-header">
        <ThemeSchemaSwitch
          :theme-schema="themeStore.themeScheme" :show-tooltip="false"
          class="text-18px" @switch="themeStore.toggleThemeScheme"
        />
        <LangSwitch
          v-if="themeStore.header.multilingual.visible"
          :lang="appStore.locale" :lang-options="appStore.localeOptions" :show-tooltip="false"
          @change-lang="appStore.changeLocale"
        />
      </div>

      <div class="form-body">
        <div class="form-tag">
          <span class="form-tag-dot"></span>
          {{ $t(activeModule.label) }}
        </div>

        <h2 class="form-title">欢迎回来<br /><span class="form-title-sub">让我们继续。</span></h2>

        <Transition :name="themeStore.page.animateMode" mode="out-in" appear>
          <component :is="activeModule.component" />
        </Transition>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login-root {
  width: 100%; height: 100%;
  display: grid;
  grid-template-columns: 1fr 1fr;
  overflow: hidden;
}
@media (max-width: 768px) {
  .login-root { grid-template-columns: 1fr; }
  .login-brand { display: none; }
}

/* ====== LEFT BRAND ====== */
.login-brand {
  background: #111111;
  display: flex; align-items: center; justify-content: center;
  position: relative; overflow: hidden;
}
.brand-inner {
  position: relative; z-index: 1;
  padding: 64px 56px;
  display: flex; flex-direction: column; justify-content: space-between;
  min-height: 100%; max-width: 440px;
}
.brand-top { display: flex; align-items: center; gap: 10px; color: rgba(255,255,255,0.6); }
.brand-app { font-size: 18px; font-weight: 600; color: rgba(255,255,255,0.8); }

.brand-hero { margin: auto 0; }
.brand-heading {
  font-size: clamp(28px, 3vw, 38px); font-weight: 600; color: rgba(255,255,255,0.9);
  line-height: 1.25; letter-spacing: -0.02em; margin: 0 0 16px;
}
.brand-accent { color: #ff385c; }
.brand-desc { font-size: 15px; color: rgba(255,255,255,0.4); line-height: 1.6; margin: 0; max-width: 360px; }

.brand-foot { color: rgba(255,255,255,0.25); font-size: 12px; }

.brand-orb {
  position: absolute; border-radius: 50%; filter: blur(100px);
  pointer-events: none;
}
.brand-orb-1 {
  width: 400px; height: 400px;
  background: rgba(255,56,92,0.12);
  top: -100px; left: -100px;
  animation: brandOrb 12s ease-in-out infinite;
}
.brand-orb-2 {
  width: 300px; height: 300px;
  background: rgba(255,107,129,0.08);
  bottom: -80px; right: -60px;
  animation: brandOrb 10s 2s ease-in-out infinite;
}
@keyframes brandOrb {
  0%, 100% { transform: scale(1); opacity: 0.6; }
  50% { transform: scale(1.2); opacity: 1; }
}

/* ====== RIGHT FORM ====== */
.login-form {
  background: #ffffff;
  display: flex; flex-direction: column;
  padding: 32px 40px;
}
:global(.dark) .login-form { background: #1a1a1a; }

.form-header {
  display: flex; justify-content: flex-end; gap: 12px;
}

.form-body {
  flex: 1; display: flex; flex-direction: column; justify-content: center;
  max-width: 400px; margin: 0 auto; width: 100%;
}

.form-tag {
  display: inline-flex; align-items: center; gap: 8px;
  font-size: 12px; font-weight: 500; color: #6a6a6a;
  letter-spacing: 0.06em; margin-bottom: 12px;
}
.form-tag-dot { width: 5px; height: 5px; border-radius: 50%; background: #ff385c; }

.form-title {
  font-size: 32px; font-weight: 600; color: #222;
  line-height: 1.2; letter-spacing: -0.02em; margin: 0 0 32px;
}
:global(.dark) .form-title { color: #e4e4e4; }
.form-title-sub { color: #ff385c; }
</style>

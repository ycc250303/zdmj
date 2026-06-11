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
    <div class="bg-image"></div>
    <div class="frost"></div>

    <header class="top-bar">
      <ThemeSchemaSwitch :theme-schema="themeStore.themeScheme" :show-tooltip="false" class="top-btn" @switch="themeStore.toggleThemeScheme" />
      <LangSwitch v-if="themeStore.header.multilingual.visible" :lang="appStore.locale" :lang-options="appStore.localeOptions" :show-tooltip="false" class="top-btn" @change-lang="appStore.changeLocale" />
    </header>

    <main class="hero">
      <div class="hero-tag"><span class="tag-dot"></span>{{ $t(activeModule.label) }}</div>
      <h1 class="hero-zh">欢迎回来</h1>
      <p class="hero-en">Sign in to continue your journey</p>

      <div class="form-wrap">
        <Transition :name="themeStore.page.animateMode" mode="out-in" appear>
          <component :is="activeModule.component" />
        </Transition>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login-root { position: relative; width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; overflow: hidden; }

.bg-image { position: absolute; inset: -20px; background: url('/images/2.jpg') center/cover no-repeat; filter: blur(4px); z-index: 0; }
.frost { position: absolute; inset: 0; background: rgba(12,12,12,0.3); z-index: 1; }

.top-bar { position: absolute; top: 0; right: 0; z-index: 3; display: flex; gap: 8px; padding: 20px 32px; }
.top-btn { color: rgba(255,255,255,0.5) !important; }
.top-btn:hover { color: rgba(255,255,255,0.8) !important; }

.hero { position: relative; z-index: 2; text-align: center; padding: 40px; max-width: 440px; width: 100%; }

.hero-tag { display: inline-flex; align-items: center; gap: 8px; font-size: 11px; font-weight: 600; letter-spacing: 0.2em; color: rgba(255,255,255,0.4); margin-bottom: 32px; }
.tag-dot { width: 5px; height: 5px; border-radius: 50%; background: rgba(255,255,255,0.6); }

.hero-zh { font-family: 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', Georgia, serif; font-size: clamp(42px, 6vw, 64px); font-weight: 700; color: rgba(255,255,255,0.9); line-height: 1; letter-spacing: -0.03em; margin: 0 0 12px; }
.hero-en { font-family: Georgia, 'Times New Roman', serif; font-size: 16px; font-style: italic; color: rgba(255,255,255,0.3); margin: 0 0 48px; }

.form-wrap { text-align: left; }
</style>

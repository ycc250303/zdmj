<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/auth';
import { useAppStore } from '@/store/modules/app';
import { localStg } from '@/utils/storage';
import { $t } from '@/locales';

defineOptions({ name: 'PortalIndex' });

const router = useRouter();
const authStore = useAuthStore();
const appStore = useAppStore();

const isLogin = computed(() => Boolean(localStg.get('token')));

const langOptions = [
  { label: '中文', key: 'zh-CN' },
  { label: 'English', key: 'en-US' }
];

function handleLangSelect(key: string | number) { appStore.changeLocale(key as App.I18n.LangType); }
function handleAction() { isLogin.value ? router.push('/home') : router.push('/login'); }

const modules = [
  { title: 'AI 对话', desc: '智能问答，实时辅助', icon: '💬', route: '/chat' },
  { title: '知识库', desc: '职场知识，随查随用', icon: '📚', route: '/knowledge' },
  { title: '简历工坊', desc: '精美模板，AI 写作', icon: '📄', route: '/resumes' },
  { title: '岗位匹配', desc: '精准匹配，人岗分析', icon: '💼', route: '/jobs' },
];
</script>

<template>
  <div class="portal-root">
    <!-- Animated Orbs -->
    <div class="bg-orbs">
      <div class="orb orb-1"></div>
      <div class="orb orb-2"></div>
    </div>

    <!-- Header -->
    <header class="portal-header">
      <div class="header-inner">
        <div class="brand">
          <span class="brand-mark">Z</span>
          <span class="brand-name">{{ $t('page.portal.title') }}</span>
        </div>
        <nav class="header-nav">
          <span class="nav-tag">AI-Powered</span>
          <span class="nav-tag outline">v2.0</span>
        </nav>
        <div class="header-actions">
          <NDropdown :options="langOptions" placement="bottom" @select="handleLangSelect">
            <NButton quaternary circle size="small"><icon-carbon-language /></NButton>
          </NDropdown>
          <template v-if="isLogin">
            <span class="greet">{{ $t('page.portal.hello') }}{{ authStore.userInfo.userName }}</span>
            <NButton type="primary" round size="small" @click="handleAction">
              {{ $t('page.portal.enterConsole') }} →
            </NButton>
          </template>
          <template v-else>
            <NButton type="primary" round @click="handleAction">
              {{ $t('page.portal.loginSystem') }} →
            </NButton>
          </template>
        </div>
      </div>
    </header>

    <!-- Hero -->
    <main class="portal-main">
      <section class="hero">
        <div class="hero-badge">
          <span class="badge-dot"></span>
          {{ $t('page.portal.heroTitle') }}
        </div>
        <h1 class="hero-headline">{{ $t('page.portal.heroDesc') }}</h1>
        <p class="hero-sub">智能对话、知识库管理、简历生成、岗位匹配——四合一智能求职解决方案</p>
        <div class="hero-cta">
          <NButton type="primary" size="large" round @click="handleAction">
            {{ isLogin ? $t('page.portal.actionEnter') : $t('page.portal.actionLogin') }}
            <span class="arrow">→</span>
          </NButton>
        </div>
      </section>

      <!-- Module Showcase -->
      <section class="modules">
        <div class="modules-grid">
          <div
            v-for="(m, idx) in modules"
            :key="m.route"
            class="module-card"
            :style="{ animationDelay: `${0.1 * idx}s` }"
            @click="router.push(m.route)"
          >
            <div class="module-icon">{{ m.icon }}</div>
            <h3 class="module-title">{{ m.title }}</h3>
            <p class="module-desc">{{ m.desc }}</p>
          </div>
        </div>
      </section>
    </main>

    <!-- Footer -->
    <footer class="portal-footer">
      <p>2026 职点迷津 · AI-Powered Career Platform</p>
    </footer>
  </div>
</template>

<style scoped>
.portal-root {
  min-height: 100%;
  background: #ffffff;
  position: relative;
  overflow: auto;
}

/* Orbs */
.bg-orbs {
  position: fixed; inset: 0; pointer-events: none; z-index: 0; overflow: hidden;
}
.orb {
  position: absolute; border-radius: 50%; filter: blur(140px); opacity: 0.10;
  animation: orbDrift 25s ease-in-out infinite;
}
.orb-1 { width: 700px; height: 700px; background: #ff385c; top: -250px; right: -150px; }
.orb-2 { width: 500px; height: 500px; background: #ff6b81; bottom: -200px; left: -100px; animation-delay: -12s; }

@keyframes orbDrift {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(50px, -30px) scale(1.06); }
  66% { transform: translate(-30px, 20px) scale(0.96); }
}

/* Header */
.portal-header {
  position: sticky; top: 0; z-index: 10;
  background: rgba(255,255,255,0.85); backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(0,0,0,0.06);
}
.header-inner {
  max-width: 1200px; margin: 0 auto; height: 64px; padding: 0 32px;
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
}
.brand { display: flex; align-items: center; gap: 10px; }
.brand-mark {
  width: 34px; height: 34px; border-radius: 10px;
  background: #ff385c; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px; font-weight: 700; font-style: italic;
}
.brand-name { font-size: 18px; font-weight: 700; color: #222; }
.header-nav { display: flex; gap: 8px; }
.nav-tag {
  font-size: 11px; font-weight: 600; padding: 3px 10px; border-radius: 99px;
  background: rgba(255,56,92,0.08); color: #ff385c; letter-spacing: 0.03em;
}
.nav-tag.outline { background: transparent; border: 1px solid #ddd; color: #6a6a6a; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.greet { font-size: 14px; color: #6a6a6a; max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* Hero */
.portal-main { position: relative; z-index: 1; }
.hero { text-align: center; padding: 100px 24px 64px; max-width: 680px; margin: 0 auto; }
.hero-badge {
  display: inline-flex; align-items: center; gap: 8px;
  font-size: 13px; color: #ff385c; font-weight: 600;
  margin-bottom: 20px;
  animation: fadeInUp 0.6s ease both;
}
.badge-dot { width: 6px; height: 6px; border-radius: 50%; background: #ff385c; animation: pulse 2s ease-in-out infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

.hero-headline {
  font-size: clamp(28px, 4vw, 44px); font-weight: 700; color: #222;
  line-height: 1.2; letter-spacing: -0.02em; margin: 0 0 16px;
  animation: fadeInUp 0.6s 0.15s ease both;
}
.hero-sub {
  font-size: 16px; color: #6a6a6a; line-height: 1.6; margin: 0 0 36px;
  animation: fadeInUp 0.6s 0.25s ease both;
}
.hero-cta { animation: fadeInUp 0.6s 0.35s ease both; }
.hero-cta .arrow { display: inline-block; transition: transform 0.2s; margin-left: 4px; }
.hero-cta .n-button:hover .arrow { transform: translateX(3px); }

@keyframes fadeInUp { from { opacity: 0; transform: translateY(24px); } to { opacity: 1; transform: translateY(0); } }

/* Module Cards */
.modules { max-width: 1000px; margin: 0 auto; padding: 0 24px 80px; }
.modules-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
@media (max-width: 800px) { .modules-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 480px) { .modules-grid { grid-template-columns: 1fr; } }

.module-card {
  background: #fff; border: 1px solid #ebebeb; border-radius: 16px;
  padding: 28px 24px; cursor: pointer;
  transition: all 0.35s cubic-bezier(0.16,1,0.3,1);
  animation: slideUp 0.5s cubic-bezier(0.16,1,0.3,1) both;
}
.module-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 12px 40px rgba(255,56,92,0.06), 0 4px 12px rgba(0,0,0,0.03);
  border-color: rgba(255,56,92,0.2);
}
.module-icon { font-size: 32px; margin-bottom: 12px; transition: transform 0.35s; }
.module-card:hover .module-icon { transform: scale(1.1); }
.module-title { font-size: 16px; font-weight: 600; color: #222; margin: 0 0 4px; }
.module-desc { font-size: 13px; color: #6a6a6a; margin: 0; }

@keyframes slideUp { from { opacity: 0; transform: translateY(30px); } to { opacity: 1; transform: translateY(0); } }

/* Footer */
.portal-footer {
  text-align: center; padding: 24px;
  font-size: 12px; color: #bbb;
  border-top: 1px solid #f0f0f0;
}
</style>

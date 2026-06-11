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

// ★★★ 替换为你的背景图片 ★★★
const bgImage = '/images/2.jpg';

const langOptions = [
  { label: '中文', key: 'zh-CN' },
  { label: 'English', key: 'en-US' }
];

function handleLangSelect(key: string | number) { appStore.changeLocale(key as App.I18n.LangType); }
function handleAction() { router.push(isLogin.value ? '/home' : '/login'); }
</script>

<template>
  <div class="portal">
    <!-- ====== 全屏背景大图 ====== -->
    <div class="bg-image" :style="{ backgroundImage: `url(${bgImage})` }"></div>

    <!-- ====== 深色毛玻璃遮罩 ====== -->
    <div class="frost"></div>

    <!-- ====== 顶部语言切换 ====== -->
    <header class="top-bar">
      <NDropdown :options="langOptions" placement="bottom" @select="handleLangSelect">
        <button class="lang-btn"><icon-carbon-language /></button>
      </NDropdown>
    </header>

    <!-- ====== 核心内容 ====== -->
    <main class="hero">
      <div class="hero-tag">
        <span class="tag-dot"></span>
        AI CAREER PLATFORM
      </div>

      <h1 class="hero-zh">职点迷津</h1>
      <p class="hero-en">Your AI-Powered Career Navigator</p>

      <!-- 名言 -->
      <div class="quote-block">
        <p class="quote-text">
          一片树林里分出两条路<br />而我选择了人迹更少的一条<br />从此决定了我一生的道路
        </p>
        <p class="quote-en">
          Two roads diverged in a wood, and I —<br />I took the one less traveled by,<br />And that has made all the difference.
        </p>
        <p class="quote-author">— Robert Frost, <em>The Road Not Taken</em></p>
      </div>

      <div class="hero-btns">
        <button class="btn-glass-dark" @click="handleAction">
          进入控制台
          <svg width="15" height="15" viewBox="0 0 15 15" fill="none">
            <path d="M3 7.5h9M8 3.5l4 4-4 4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <button class="btn-glass" @click="handleAction">
          登录系统
        </button>
      </div>
    </main>
  </div>
</template>

<style scoped>
.portal {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

/* ====== 背景大图（微模糊） ====== */
.bg-image {
  position: absolute;
  inset: -20px;
  background: center/cover no-repeat;
  filter: blur(4px);
  z-index: 0;
}

/* ====== 轻透暗色蒙版 ====== */
.frost {
  position: absolute;
  inset: 0;
  background: rgba(12,12,12,0.3);
  z-index: 1;
}

/* ====== 顶部 ====== */
.top-bar {
  position: absolute;
  top: 0; left: 0; right: 0;
  z-index: 3;
  display: flex;
  justify-content: flex-end;
  padding: 20px 32px;
}
.lang-btn {
  width: 40px; height: 40px;
  border-radius: 50%;
  border: 1px solid rgba(255,255,255,0.12);
  background: rgba(255,255,255,0.08);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  color: rgba(255,255,255,0.5);
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
  transition: all 0.25s;
}
.lang-btn:hover {
  background: rgba(255,255,255,0.18);
  border-color: rgba(255,255,255,0.25);
  color: rgba(255,255,255,0.8);
}

/* ====== 核心文字区 ====== */
.hero {
  position: relative;
  z-index: 2;
  text-align: center;
  padding: 40px;
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.2em;
  color: rgba(255,255,255,0.4);
  margin-bottom: 40px;
}
.tag-dot {
  width: 5px; height: 5px;
  border-radius: 50%;
  background: rgba(255,255,255,0.6);
}

.hero-zh {
  font-family: 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', Georgia, serif;
  font-size: clamp(64px, 10vw, 120px);
  font-weight: 700;
  color: rgba(255,255,255,0.9);
  line-height: 1;
  letter-spacing: -0.03em;
  margin: 0 0 16px;
}

.hero-en {
  font-family: Georgia, 'Times New Roman', serif;
  font-size: clamp(16px, 2.5vw, 22px);
  font-style: italic;
  color: rgba(255,255,255,0.35);
  margin: 0 0 48px;
  letter-spacing: 0.02em;
}

/* ====== 名言 ====== */
.quote-block {
  margin-bottom: 48px;
}
.quote-text {
  font-family: 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', serif;
  font-size: 17px;
  color: rgba(255,255,255,0.42);
  line-height: 2;
  margin: 0 0 18px;
  letter-spacing: 0.04em;
}
.quote-en {
  font-family: Georgia, 'Times New Roman', serif;
  font-size: 14px;
  font-style: italic;
  color: rgba(255,255,255,0.25);
  line-height: 2;
  margin: 0 0 12px;
  letter-spacing: 0.02em;
}
.quote-author {
  font-size: 11px;
  color: rgba(255,255,255,0.18);
  letter-spacing: 0.06em;
  margin: 0;
}
.quote-author em {
  font-style: italic;
  color: rgba(255,255,255,0.22);
}

/* ====== 按钮行 ====== */
.hero-btns {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
  margin-bottom: 40px;
}

/* ====== 底部功能标签 ====== */
.hero-footer {
  font-size: 12px;
  color: rgba(255,255,255,0.2);
  letter-spacing: 0.08em;
  margin: 0;
}
</style>

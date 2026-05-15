<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { $t } from '@/locales';

const router = useRouter();

// 快速操作功能卡片
interface QuickAction {
  id: number;
  title: string;
  description: string;
  icon: string;
  route: string;
  accent: string; // 卡片强调色（用于 hover 边框、图标背景）
  positionClass: string;
}

const quickActions = computed<QuickAction[]>(() => [
  {
    id: 1,
    title: $t('page.home.aiChat.title'),
    description: $t('page.home.aiChat.description'),
    icon: '🤖',
    route: '/chat',
    accent: '#3b82f6',
    positionClass: 'card-1'
  },
  {
    id: 2,
    title: $t('page.home.knowledge.title'),
    description: $t('page.home.knowledge.description'),
    icon: '📖',
    route: '/knowledge',
    accent: '#a855f7',
    positionClass: 'card-2'
  },
  {
    id: 3,
    title: $t('page.home.resume.title'),
    description: $t('page.home.resume.description'),
    icon: '📑',
    route: '/resumes',
    accent: '#10b981',
    positionClass: 'card-3'
  },
  {
    id: 4,
    title: $t('page.home.jobs.title'),
    description: $t('page.home.jobs.description'),
    icon: '💼',
    route: '/jobs',
    accent: '#f97316',
    positionClass: 'card-4'
  }
]);

function navigateTo(route: string) {
  router.push(route);
}
</script>

<template>
  <div class="home-container">
    <!-- 背景装饰元素 -->
    <div class="hero-bg">
      <div class="bg-gradient-1"></div>
      <div class="bg-gradient-2"></div>
      <div class="bg-gradient-3"></div>
      <div class="bg-dots"></div>
      <div class="bg-waves"></div>
    </div>

    <!-- 主内容区 -->
    <div class="hero-section">
      <!-- 标题 -->
      <div class="hero-content">
        <span class="hero-badge">{{ $t('page.home.hero.badge') }}</span>
        <h1 class="hero-title">{{ $t('page.home.hero.title') }}</h1>
        <p class="hero-subtitle">{{ $t('page.home.hero.subtitle') }}</p>
      </div>

      <!-- 功能卡片网格 -->
      <div class="action-grid">
        <div
          v-for="action in quickActions"
          :key="action.id"
          class="action-card"
          :style="{ '--accent': action.accent } as any"
          @click="navigateTo(action.route)"
        >
          <div class="action-icon" :style="{ background: `${action.accent}1a`, color: action.accent }">
            <span>{{ action.icon }}</span>
          </div>
          <div class="action-body">
            <h3 class="action-title">{{ action.title }}</h3>
            <p class="action-desc">{{ action.description }}</p>
          </div>
          <span class="action-arrow" :style="{ color: action.accent }">→</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-container {
  position: relative;
  min-height: 100%;
  height: 100%;
  overflow: hidden;
  background: linear-gradient(135deg, #f8fafc 0%, #eef2f7 50%, #f5f7fa 100%);
}

:global(.dark) .home-container {
  background: linear-gradient(135deg, #0f172a 0%, #1e293b 50%, #0f172a 100%);
}

/* ---------------- 背景装饰 ---------------- */
.hero-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
}

.bg-gradient-1 {
  position: absolute;
  top: -10%;
  right: -8%;
  width: 55%;
  height: 55%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.18) 0%, transparent 70%);
  animation: pulse 8s ease-in-out infinite;
}

.bg-gradient-2 {
  position: absolute;
  bottom: -8%;
  left: -10%;
  width: 50%;
  height: 50%;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.16) 0%, transparent 70%);
  animation: pulse 10s ease-in-out infinite reverse;
}

.bg-gradient-3 {
  position: absolute;
  top: 35%;
  left: 30%;
  width: 35%;
  height: 35%;
  background: radial-gradient(circle, rgba(16, 185, 129, 0.12) 0%, transparent 60%);
  animation: pulse 12s ease-in-out infinite;
}

.bg-dots {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle, #cbd5e1 1px, transparent 1px);
  background-size: 28px 28px;
  opacity: 0.25;
}

.bg-waves {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 200px;
  background: linear-gradient(180deg, transparent 0%, rgba(255, 255, 255, 0.5) 100%);
}

:global(.dark) .bg-waves {
  background: linear-gradient(180deg, transparent 0%, rgba(15, 23, 42, 0.5) 100%);
}

:global(.dark) .bg-dots {
  background-image: radial-gradient(circle, #334155 1px, transparent 1px);
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 0.7; }
  50% { transform: scale(1.1); opacity: 0.9; }
}

/* ---------------- 主区域 ---------------- */
.hero-section {
  position: relative;
  z-index: 10;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 56px 24px;
  gap: 56px;
}

/* 标题区 */
.hero-content {
  text-align: center;
  max-width: 900px;
}

.hero-badge {
  display: inline-block;
  padding: 6px 16px;
  border-radius: 999px;
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 20px;
  border: 1px solid rgba(99, 102, 241, 0.2);
}

.hero-title {
  font-size: 56px;
  font-weight: 800;
  background: linear-gradient(135deg, #1e293b 0%, #475569 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 16px;
  line-height: 1.2;
}

:global(.dark) .hero-title {
  background: linear-gradient(135deg, #e2e8f0 0%, #94a3b8 100%);
  -webkit-background-clip: text;
  background-clip: text;
}

.hero-subtitle {
  font-size: 18px;
  color: #64748b;
  line-height: 1.7;
}

:global(.dark) .hero-subtitle {
  color: #94a3b8;
}

/* ---------------- 功能卡片网格 ---------------- */
.action-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  width: 100%;
  max-width: 1080px;
}

.action-card {
  --accent: #6366f1;
  position: relative;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 20px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06), 0 2px 8px rgba(15, 23, 42, 0.04);
  transition: transform 0.35s cubic-bezier(0.4, 0, 0.2, 1),
              box-shadow 0.35s ease,
              border-color 0.35s ease;
  overflow: hidden;
}

:global(.dark) .action-card {
  background: rgba(30, 41, 59, 0.85);
  border-color: rgba(71, 85, 105, 0.5);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3), 0 2px 8px rgba(0, 0, 0, 0.2);
}

:global(.dark) .action-card:hover {
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4), 0 4px 12px rgba(0, 0, 0, 0.3);
}

.action-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: linear-gradient(90deg, transparent 0%, var(--accent) 50%, transparent 100%);
  opacity: 0;
  transition: opacity 0.35s ease;
}

.action-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 20px 40px rgba(15, 23, 42, 0.12), 0 4px 12px rgba(15, 23, 42, 0.06);
  border-color: color-mix(in srgb, var(--accent) 30%, white);
}

.action-card:hover::before {
  opacity: 1;
}

.action-card:hover .action-arrow {
  transform: translateX(4px);
  opacity: 1;
}

/* 图标圆形容器 */
.action-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  transition: transform 0.35s ease;
}

.action-card:hover .action-icon {
  transform: scale(1.08) rotate(-3deg);
}

.action-body {
  flex: 1;
  min-height: 60px;
}

.action-title {
  font-size: 17px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 6px;
}

:global(.dark) .action-title {
  color: #e2e8f0;
}

.action-desc {
  font-size: 12.5px;
  color: #64748b;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

:global(.dark) .action-desc {
  color: #94a3b8;
}

.action-arrow {
  position: absolute;
  right: 20px;
  bottom: 18px;
  font-size: 18px;
  font-weight: 600;
  opacity: 0.6;
  transition: transform 0.35s ease, opacity 0.35s ease;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 1024px) {
  .action-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    max-width: 640px;
  }
  .hero-title { font-size: 44px; }
}

@media (max-width: 640px) {
  .hero-section {
    padding: 40px 16px;
    gap: 36px;
  }
  .hero-title { font-size: 32px; }
  .hero-subtitle { font-size: 15px; }
  .action-grid {
    grid-template-columns: 1fr;
    max-width: 360px;
  }
  .action-card {
    padding: 18px;
  }
}
</style>

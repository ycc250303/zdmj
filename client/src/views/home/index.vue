<script setup lang="ts">
import { ref } from 'vue';
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
  color: string;
  positionClass: string;
}

const quickActions = ref<QuickAction[]>([
  {
    id: 1,
    title: $t('page.home.aiChat.title'),
    description: $t('page.home.aiChat.description'),
    icon: 'i-mdi-robot',
    route: '/chat',
    color: 'from-blue-50 to-blue-100',
    positionClass: 'card-1'
  },
  {
    id: 2,
    title: $t('page.home.knowledge.title'),
    description: $t('page.home.knowledge.description'),
    icon: 'i-mdi-book-open-page-variant',
    route: '/knowledge',
    color: 'from-purple-50 to-purple-100',
    positionClass: 'card-2'
  },
  {
    id: 3,
    title: $t('page.home.resume.title'),
    description: $t('page.home.resume.description'),
    icon: 'i-mdi-file-document-multiple',
    route: '/resumes',
    color: 'from-emerald-50 to-emerald-100',
    positionClass: 'card-3'
  },
  {
    id: 4,
    title: '岗位信息',
    description: '浏览和管理岗位信息，生成岗位能力画像',
    icon: 'i-mdi-briefcase',
    route: '/jobs',
    color: 'from-orange-50 to-orange-100',
    positionClass: 'card-4'
  }
]);

function navigateTo(route: string) {
  router.push(route);
}
</script>

<template>
  <div class="home-container">
    <!-- Hero 区域 -->
    <div class="hero-section">
      <!-- 背景装饰元素 -->
      <div class="hero-bg">
        <div class="bg-gradient-1"></div>
        <div class="bg-gradient-2"></div>
        <div class="bg-gradient-3"></div>
        <div class="bg-dots"></div>
        <div class="bg-waves"></div>
      </div>

      <div class="hero-content">
        <h1 class="hero-title">{{ $t('page.home.hero.title') }}</h1>
        <p class="hero-subtitle">{{ $t('page.home.hero.subtitle') }}</p>
      </div>

      <!-- 浮动功能卡片 -->
      <div class="hero-illustration">
        <div
          v-for="action in quickActions"
          :key="action.id"
          :class="['floating-card', 'action-card', action.positionClass]"
          @click="navigateTo(action.route)"
        >
          <!-- 默认显示的大标题 -->
          <div class="card-default">
            <h3 class="card-large-title">{{ action.title }}</h3>
          </div>

          <!-- Hover时显示的详细内容 -->
          <div class="card-detail">
            <div class="card-icon" :class="action.icon"></div>
            <div class="card-content">
              <h3 class="card-title">{{ action.title }}</h3>
              <p class="card-description">{{ action.description }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.home-container {
  height: 100vh;
  overflow: hidden;
}

/* Hero 区域 */
.hero-section {
  position: relative;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  overflow: hidden;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 50%, #f0f4f8 100%);
}

/* 背景装饰 */
.hero-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  z-index: 1;
}

.bg-gradient-1 {
  position: absolute;
  top: -20%;
  right: -10%;
  width: 60%;
  height: 60%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.1) 0%, transparent 70%);
  animation: pulse 8s ease-in-out infinite;
}

.bg-gradient-2 {
  position: absolute;
  bottom: -10%;
  left: -10%;
  width: 50%;
  height: 50%;
  background: radial-gradient(circle, rgba(168, 85, 247, 0.1) 0%, transparent 70%);
  animation: pulse 10s ease-in-out infinite reverse;
}

.bg-gradient-3 {
  position: absolute;
  top: 40%;
  left: 30%;
  width: 40%;
  height: 40%;
  background: radial-gradient(circle, rgba(16, 185, 129, 0.08) 0%, transparent 60%);
  animation: pulse 12s ease-in-out infinite;
}

.bg-dots {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: radial-gradient(circle, #cbd5e1 1px, transparent 1px);
  background-size: 30px 30px;
  opacity: 0.3;
}

.bg-waves {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 200px;
  background: linear-gradient(180deg, transparent 0%, rgba(255, 255, 255, 0.5) 100%);
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
    opacity: 0.6;
  }
  50% {
    transform: scale(1.1);
    opacity: 0.8;
  }
}

.hero-content {
  max-width: 1200px;
  margin: 0 auto;
  margin-bottom: 120px;
  text-align: center;
  position: relative;
  z-index: 10;
}

.hero-title {
  font-size: 72px;
  font-weight: 800;
  background: linear-gradient(135deg, #1e293b 0%, #475569 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 24px;
  line-height: 1.2;
}

.hero-subtitle {
  font-size: 26px;
  color: #64748b;
  max-width: 700px;
  margin-left: auto;
  margin-right: auto;
  line-height: 1.6;
}

/* 浮动卡片动画 */
.hero-illustration {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  z-index: 5;
}

.floating-card {
  position: absolute;
  width: 180px;
  height: 120px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 20px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1), 0 2px 8px rgba(0, 0, 0, 0.05);
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  animation: float 6s ease-in-out infinite;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  pointer-events: auto;
  position: relative;
  overflow: hidden;
}

.floating-card:hover {
  width: 220px;
  height: 180px;
  padding: 20px;
  transform: translateY(0) scale(1);
  animation-play-state: paused;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.15), 0 4px 16px rgba(0, 0, 0, 0.1);
  z-index: 20;
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(255, 255, 255, 1);
}

/* 默认显示的大标题 */
.card-default {
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s ease;
}

.floating-card:hover .card-default {
  opacity: 0;
  transform: scale(0.8);
  pointer-events: none;
}

.card-large-title {
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(135deg, #1e293b 0%, #475569 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

/* Hover时显示的详细内容 */
.card-detail {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
  opacity: 0;
  transform: scale(0.8);
  transition: all 0.3s ease;
  pointer-events: none;
}

.floating-card:hover .card-detail {
  opacity: 1;
  transform: scale(1);
  pointer-events: auto;
}

.card-1 {
  top: 40%;
  left: 8%;
  animation-delay: 0s;
}

.card-2 {
  top: 40%;
  left: calc(100% - 8% - 180px); /* 右侧，减去卡片宽度 */
  animation-delay: 2s;
}

.card-3 {
  bottom: 10%;
  left: 50%;
  margin-left: -90px; /* 卡片宽度的一半 */
  animation-delay: 4s;
}

.card-3:hover {
  margin-left: -90px; /* 保持hover时位置不变 */
}

.card-4 {
  top: 20%;
  left: 50%;
  margin-left: -90px;
  animation-delay: 1s;
}

.card-4:hover {
  margin-left: -90px;
}

.card-4 .card-icon {
  color: #f97316;
}

.card-icon {
  font-size: 48px;
  margin-bottom: 12px;
  transition: transform 0.3s ease;
}

.card-1 .card-icon {
  color: #3b82f6;
}

.card-2 .card-icon {
  color: #a855f7;
}

.card-3 .card-icon {
  color: #10b981;
}

.card-title {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 4px;
}

.card-description {
  font-size: 12px;
  color: #64748b;
  line-height: 1.4;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px);
  }
  50% {
    transform: translateY(-20px);
  }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .hero-section {
    height: 100vh;
    padding: 40px 24px;
  }

  .hero-title {
    font-size: 40px;
  }

  .hero-subtitle {
    font-size: 18px;
  }

  .floating-card {
    width: 140px;
    height: 120px;
    padding: 12px;
  }

  .card-icon {
    font-size: 36px;
    margin-bottom: 8px;
  }

  .card-title {
    font-size: 14px;
  }

  .card-description {
    font-size: 10px;
  }

  .card-1 {
    top: 12%;
    left: 20%;
  }

  .card-2 {
    top: 12%;
    left: calc(100% - 20% - 140px); /* 移动端右侧 */
  }

  .card-3 {
    bottom: 12%;
    left: 55%;
    margin-left: -70px; /* 移动端卡片宽度的一半 */
  }

  .card-3:hover {
    margin-left: -70px;
  }

  .card-4 {
    top: 15%;
    left: 55%;
    margin-left: -70px;
  }

  .card-4:hover {
    margin-left: -70px;
  }
}
</style>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();

const slides = [
  '/images/3.jpg',
  '/images/4.jpg',
  '/images/5.jpg',
  '/images/6.jpg',
  '/images/3.jpg',
  '/images/4.jpg',
  '/images/5.jpg',
  '/images/6.jpg',
];

const count = slides.length;
const angleStep = 360 / count;

const ringEl = ref<HTMLDivElement | null>(null);
const visibleIndices = ref(new Set<number>());
let currentAngle = 0;
let rafId = 0;
let lastTime = 0;
let frameCount = 0;
const ROTATE_SPEED = 18;

function animate(time: number) {
  if (!lastTime) lastTime = time;
  const dt = Math.min((time - lastTime) / 1000, 0.1);
  lastTime = time;
  currentAngle = (currentAngle + ROTATE_SPEED * dt) % 360;
  if (ringEl.value) ringEl.value.style.transform = `rotateY(${-currentAngle}deg)`;

  frameCount++;
  if (frameCount % 3 === 0) {
    const visible = new Set<number>();
    for (let i = 0; i < count; i++) {
      if (Math.abs(((angleStep * i - currentAngle) % 360 + 540) % 360 - 180) < 85) visible.add(i);
    }
    visibleIndices.value = visible;
  }
  rafId = requestAnimationFrame(animate);
}

onMounted(() => { rafId = requestAnimationFrame(animate); });
onBeforeUnmount(() => { cancelAnimationFrame(rafId); });
</script>

<template>
  <div class="home">
    <!-- ====== 左栏 ====== -->
    <section class="text-col">
      <div class="kicker">
        <span class="kicker-rule"></span>
        <span>职点迷津 · AI CAREER PLATFORM</span>
      </div>

      <h1 class="headline">
        <span class="hl-top">让 AI 助你</span>
        <span class="hl-bot">找到<span class="hl-accent">理想工作</span></span>
      </h1>

      <p class="deck">
        Discover, evaluate and grow your career —<br />
        <em>powered by artificial intelligence.</em>
      </p>

      <div class="sep">
        <span class="sep-line"></span>
        <span class="sep-dot">◆</span>
        <span class="sep-line"></span>
      </div>

      <p class="desc">智能对话、知识库管理、简历生成，一站式求职解决方案</p>

      <!-- ====== 液态玻璃入口 ====== -->
      <div class="entry-wrap">
        <!-- 背景色块 —— 让玻璃有东西可以模糊 -->
        <div class="glass-bg-shape gbs-1"></div>
        <div class="glass-bg-shape gbs-2"></div>
        <div class="glass-bg-shape gbs-3"></div>

        <div class="entry-grid">
        <!-- AI 对话 + 知识库：并排 -->
        <button class="entry-card card-white" @click="router.push('/chat')">
          <span class="card-icon">💬</span>
          <span class="card-text">
            <span class="card-zh">AI 对话</span>
            <span class="card-en">Intelligent Chat</span>
          </span>
          <svg class="card-arrow" width="15" height="15" viewBox="0 0 15 15" fill="none">
            <path d="M3 7.5h9M8 3.5l4 4-4 4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>

        <button class="entry-card card-white" @click="router.push('/knowledge')">
          <span class="card-icon">📚</span>
          <span class="card-text">
            <span class="card-zh">知识库</span>
            <span class="card-en">Knowledge Base</span>
          </span>
          <svg class="card-arrow" width="15" height="15" viewBox="0 0 15 15" fill="none">
            <path d="M3 7.5h9M8 3.5l4 4-4 4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>

        <!-- 简历工坊：独占一行，大卡片，渐变 -->
        <button class="entry-card card-hero" @click="router.push('/resumes')">
          <span class="card-icon">📄</span>
          <span class="card-text">
            <span class="card-zh">简历工坊</span>
            <span class="card-en">Resume Studio — Craft your professional story</span>
          </span>
          <svg class="card-arrow" width="15" height="15" viewBox="0 0 15 15" fill="none">
            <path d="M3 7.5h9M8 3.5l4 4-4 4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>

        <!-- 岗位探索 + 空占位 -->
        <button class="entry-card card-dark" @click="router.push('/jobs')">
          <span class="card-icon">💼</span>
          <span class="card-text">
            <span class="card-zh">岗位探索</span>
            <span class="card-en">Job Discovery</span>
          </span>
          <svg class="card-arrow" width="15" height="15" viewBox="0 0 15 15" fill="none">
            <path d="M3 7.5h9M8 3.5l4 4-4 4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>

        <button class="entry-card card-white" @click="router.push('/profile/capability')">
          <span class="card-icon">🧠</span>
          <span class="card-text">
            <span class="card-zh">能力画像</span>
            <span class="card-en">Capability Profile</span>
          </span>
          <svg class="card-arrow" width="15" height="15" viewBox="0 0 15 15" fill="none">
            <path d="M3 7.5h9M8 3.5l4 4-4 4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        </div>
      </div>
    </section>

    <!-- ====== 右栏 ====== -->
    <section class="visual-col">
      <div class="ring-scene">
        <div ref="ringEl" class="ring">
          <div
            v-for="(url, idx) in slides" :key="idx"
            class="ring-card"
            :class="{ visible: visibleIndices.has(idx) }"
            :style="{ transform: `rotateY(${angleStep * idx}deg) translateZ(440px)` }"
          >
            <img :src="url" alt="" class="ring-img" />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* ============================================================ */
.home {
  min-height: 100%;
  background: #fefefe;
  display: grid;
  grid-template-columns: 1fr 1fr;
}

@media (max-width: 860px) {
  .home { grid-template-columns: 1fr; }
  .visual-col { display: none; }
}

/* ====== 左栏 ====== */
.text-col {
  display: flex; flex-direction: column; justify-content: center;
  padding: 64px 48px 64px 64px;
}

.kicker { display: flex; align-items: center; gap: 10px; font-size: 11px; font-weight: 600; letter-spacing: 0.2em; color: #333; text-transform: uppercase; margin-bottom: 48px; }
.kicker-rule { display: block; width: 28px; height: 2px; background: #c4a46c; }

.headline { display: flex; flex-direction: column; gap: 2px; margin: 0 0 36px; }
.hl-top { font-family: 'Noto Serif SC','Source Han Serif SC','Songti SC',Georgia,serif; font-size: clamp(42px,6vw,76px); font-weight: 700; color: #1a1a1a; line-height: 1.08; letter-spacing: -0.02em; }
.hl-bot { font-family: 'Noto Serif SC','Source Han Serif SC','Songti SC',Georgia,serif; font-size: clamp(46px,6.5vw,84px); font-weight: 700; color: #1a1a1a; line-height: 1.08; letter-spacing: -0.025em; margin-left: clamp(32px,7vw,100px); }
.hl-accent { color: #c4a46c; font-style: italic; }

.deck { font-family: Georgia,'Noto Serif SC','Times New Roman',serif; font-size: 16px; color: #555; line-height: 1.7; font-style: italic; margin: 0 0 28px; max-width: 380px; }
.deck em { font-style: normal; color: #333; }

.sep { display: flex; align-items: center; gap: 14px; margin-bottom: 24px; }
.sep-line { width: 48px; height: 1px; background: #ddd; }
.sep-dot { font-size: 7px; color: #c4a46c; }

.desc { font-family: -apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif; font-size: 15px; color: #666; line-height: 1.8; letter-spacing: 0.03em; margin: 0 0 36px; }

/* ====== 液态玻璃入口 ====== */
.entry-wrap {
  position: relative;
}

/* 背景色块 —— 让玻璃有东西可以模糊 */
.glass-bg-shape {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.gbs-1 {
  width: 120px; height: 120px;
  background: #e8f4f8;
  top: -20px; left: 10%;
}
.gbs-2 {
  width: 90px; height: 90px;
  background: #fef3e4;
  top: 60px; right: 20%;
}
.gbs-3 {
  width: 100px; height: 100px;
  background: #f0e6f6;
  bottom: -10px; left: 40%;
}

.entry-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  position: relative;
  z-index: 1;
}

.entry-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border-radius: 16px;
  border: none;
  cursor: pointer;
  transition: all 0.35s cubic-bezier(0.16, 1, 0.3, 1);
  text-align: left;
  position: relative;
  overflow: hidden;
}

.entry-card::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(255,255,255,0.6) 0%, rgba(255,255,255,0) 50%);
  pointer-events: none;
}

.entry-card:active { transform: scale(0.97); }

/* 白玻璃 */
.card-white {
  background: rgba(255,255,255,0.55);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  border: 1px solid rgba(255,255,255,0.6);
  color: #1a1a1a;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04), 0 4px 12px rgba(0,0,0,0.03);
}
.card-white:hover {
  background: rgba(255,255,255,0.75);
  border-color: rgba(0,0,0,0.12);
  transform: translateY(-2px);
  box-shadow: 0 2px 6px rgba(0,0,0,0.06), 0 8px 24px rgba(0,0,0,0.05);
}

/* 暗玻璃 */
.card-dark {
  background: rgba(30,30,30,0.75);
  backdrop-filter: saturate(180%) blur(24px);
  -webkit-backdrop-filter: saturate(180%) blur(24px);
  border: 1px solid rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.9);
  box-shadow: 0 1px 3px rgba(0,0,0,0.1), 0 4px 12px rgba(0,0,0,0.08);
}
.card-dark::after {
  background: linear-gradient(135deg, rgba(255,255,255,0.08) 0%, rgba(255,255,255,0) 50%);
}
.card-dark:hover {
  background: rgba(30,30,30,0.85);
  border-color: rgba(255,255,255,0.2);
  transform: translateY(-2px);
  box-shadow: 0 2px 6px rgba(0,0,0,0.15), 0 8px 28px rgba(0,0,0,0.12);
}
.card-dark .card-en { color: rgba(255,255,255,0.45); }
.card-dark .card-arrow { color: rgba(255,255,255,0.25); }
.card-dark:hover .card-arrow { color: #c4a46c; }

/* 主推卡片 —— 独占一行 */
.card-hero {
  grid-column: 1 / -1;
  background: rgba(255,255,255,0.6);
  backdrop-filter: saturate(180%) blur(24px);
  -webkit-backdrop-filter: saturate(180%) blur(24px);
  border: 1px solid rgba(255,255,255,0.7);
  color: #1a1a1a;
  padding: 18px 22px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04), 0 6px 16px rgba(0,0,0,0.04);
}
.card-hero:hover {
  background: rgba(255,255,255,0.8);
  border-color: rgba(0,0,0,0.1);
  transform: translateY(-3px);
  box-shadow: 0 2px 6px rgba(0,0,0,0.06), 0 12px 32px rgba(0,0,0,0.06);
}
.card-hero .card-en { color: rgba(0,0,0,0.35); }
.card-hero .card-arrow { color: rgba(0,0,0,0.15); }
.card-hero:hover .card-arrow { color: #c4a46c; }

/* 卡片内部 */
.card-icon { font-size: 20px; flex-shrink: 0; transition: transform 0.35s; }
.entry-card:hover .card-icon { transform: scale(1.1); }

.card-text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.card-zh { font-size: 14px; font-weight: 600; }
.card-en { font-size: 11px; color: rgba(0,0,0,0.35); letter-spacing: 0.02em; }

.card-arrow {
  flex-shrink: 0;
  color: rgba(0,0,0,0.15);
  transition: all 0.35s;
}
.entry-card:hover .card-arrow { transform: translateX(2px); color: #c4a46c; }

/* ====== 右栏 ====== */
.visual-col { display: flex; align-items: center; justify-content: center; overflow: hidden; }
.ring-scene { width: 280px; height: 400px; position: relative; perspective: 1600px; transform-style: preserve-3d; }
.ring { width: 100%; height: 100%; position: relative; transform-style: preserve-3d; will-change: transform; }
.ring-card { position: absolute; inset: 0; width: 100%; height: 100%; backface-visibility: hidden; -webkit-backface-visibility: hidden; visibility: hidden; }
.ring-card.visible { visibility: visible; }
.ring-img { width: 100%; height: 100%; object-fit: cover; display: block; border-radius: 14px; }

@media (max-width: 860px) {
  .text-col { padding: 48px 32px; }
}
</style>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();

// 图片 —— 替换为你的
const slides = [
  'https://picsum.photos/seed/r1/500/700',
  'https://picsum.photos/seed/r2/500/700',
  'https://picsum.photos/seed/r3/500/700',
  'https://picsum.photos/seed/r4/500/700',
  'https://picsum.photos/seed/r5/500/700',
  'https://picsum.photos/seed/r6/500/700',
  'https://picsum.photos/seed/r7/500/700',
  'https://picsum.photos/seed/r8/500/700',
];

const count = slides.length;
const angleStep = 360 / count;

// 用 RAF 驱动连续旋转
const ringEl = ref<HTMLDivElement | null>(null);
let currentAngle = 0;
let rafId = 0;
let lastTime = 0;
const ROTATE_SPEED = 18; // 度/秒

function animate(time: number) {
  if (!lastTime) lastTime = time;
  const dt = Math.min((time - lastTime) / 1000, 0.1);
  lastTime = time;

  currentAngle += ROTATE_SPEED * dt;
  if (ringEl.value) {
    ringEl.value.style.transform = `rotateY(${-currentAngle}deg)`;
  }
  rafId = requestAnimationFrame(animate);
}

onMounted(() => { rafId = requestAnimationFrame(animate); });
onBeforeUnmount(() => { cancelAnimationFrame(rafId); });

const shortcuts = [
  { icon: '💬', label: 'AI 对话', route: '/chat' },
  { icon: '📚', label: '知识库', route: '/knowledge' },
  { icon: '📄', label: '简历工坊', route: '/resumes' },
  { icon: '💼', label: '岗位探索', route: '/jobs' },
];
</script>

<template>
  <div class="home">
    <!-- ====== 左栏：文字 ====== -->
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

      <div class="entry-strip">
        <button
          v-for="item in shortcuts" :key="item.route"
          class="entry-pill" @click="router.push(item.route)"
        >
          <span class="ep-icon">{{ item.icon }}</span>
          <span class="ep-label">{{ item.label }}</span>
          <svg class="ep-arrow" width="13" height="13" viewBox="0 0 13 13" fill="none">
            <path d="M4.5 2.5l4 4-4 4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
      </div>
    </section>

    <!-- ====== 右栏：3D 环形旋转 ====== -->
    <section class="visual-col">
      <div class="ring-scene">
        <div ref="ringEl" class="ring">
          <div
            v-for="(url, idx) in slides"
            :key="idx"
            class="ring-card"
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
/* ============================================================
   左文右影 · CSS 3D 环形旋转（RAF 驱动，连续转圈）
   ============================================================ */

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

.kicker {
  display: flex; align-items: center; gap: 10px;
  font-size: 11px; font-weight: 600; letter-spacing: 0.2em;
  color: #333; text-transform: uppercase; margin-bottom: 48px;
}
.kicker-rule { display: block; width: 28px; height: 2px; background: #ff385c; }

.headline { display: flex; flex-direction: column; gap: 2px; margin: 0 0 36px; }

.hl-top {
  font-family: 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', Georgia, serif;
  font-size: clamp(42px, 6vw, 76px); font-weight: 700;
  color: #1a1a1a; line-height: 1.08; letter-spacing: -0.02em;
}
.hl-bot {
  font-family: 'Noto Serif SC', 'Source Han Serif SC', 'Songti SC', Georgia, serif;
  font-size: clamp(46px, 6.5vw, 84px); font-weight: 700;
  color: #1a1a1a; line-height: 1.08; letter-spacing: -0.025em;
  margin-left: clamp(32px, 7vw, 100px);
}
.hl-accent { color: #ff385c; font-style: italic; }

.deck {
  font-family: Georgia, 'Noto Serif SC', 'Times New Roman', serif;
  font-size: 16px; color: #555; line-height: 1.7;
  font-style: italic; margin: 0 0 28px; max-width: 380px;
}
.deck em { font-style: normal; color: #333; }

.sep { display: flex; align-items: center; gap: 14px; margin-bottom: 24px; }
.sep-line { width: 48px; height: 1px; background: #ddd; }
.sep-dot { font-size: 7px; color: #ff385c; }

.desc {
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  font-size: 15px; color: #666; line-height: 1.8; letter-spacing: 0.03em;
  margin: 0 0 40px;
}

.entry-strip { display: flex; gap: 8px; flex-wrap: wrap; }

.entry-pill {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 9px 18px; border-radius: 9999px;
  background: #fff; border: 1px solid #e0e0e0;
  color: #444; font-size: 13px; font-weight: 500;
  cursor: pointer; transition: all 0.22s ease;
}
.entry-pill:hover { border-color: #ff385c; color: #ff385c; }
.ep-icon { font-size: 15px; }
.ep-arrow { color: #c0c0c0; transition: all 0.22s; }
.entry-pill:hover .ep-arrow { color: #ff385c; transform: translateX(2px); }

/* ====== 右栏：3D 环形 ====== */
.visual-col {
  display: flex; align-items: center; justify-content: center;
  overflow: hidden;
}

.ring-scene {
  width: 340px;
  height: 460px;
  position: relative;
  perspective: 1600px;
  transform-style: preserve-3d;
}

.ring {
  width: 100%;
  height: 100%;
  position: relative;
  transform-style: preserve-3d;
  will-change: transform;
}

.ring-card {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border-radius: 14px;
  overflow: hidden;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  background: transparent;
  outline: none;
  border: none;
}

.ring-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  outline: none;
  border: none;
}

@media (max-width: 860px) {
  .text-col { padding: 48px 32px; }
}
</style>

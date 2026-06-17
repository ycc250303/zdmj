<script lang="ts" setup>
import { onMounted, onBeforeUnmount, ref, computed } from 'vue';

defineOptions({ name: 'AuroraStage' });

interface Props {
  /** Variant changes the orb composition. */
  variant?: 'portal' | 'auth' | 'panel';
  /** Whether to show the radial vignette overlay. */
  vignette?: boolean;
  /** Whether to show the perspective grid floor. */
  grid?: boolean;
  /** Optional custom orb colors (overrides defaults). */
  palette?: string[];
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'portal',
  vignette: true,
  grid: true
});

const stageRef = ref<HTMLDivElement | null>(null);
const px = ref(0);
const py = ref(0);
let raf = 0;

const palette = computed<string[]>(() => {
  if (props.palette?.length) return props.palette;
  switch (props.variant) {
    case 'auth':
      return ['#7C5CFF', '#22D3EE', '#FB7185', '#F59E0B'];
    case 'panel':
      return ['#7C5CFF', '#22D3EE', '#34D399'];
    default:
      return ['#7C5CFF', '#22D3EE', '#FB7185', '#34D399'];
  }
});

function handleMove(e: MouseEvent) {
  if (raf) cancelAnimationFrame(raf);
  raf = requestAnimationFrame(() => {
    const rect = stageRef.value?.getBoundingClientRect();
    if (!rect) return;
    const x = (e.clientX - rect.left) / rect.width - 0.5;
    const y = (e.clientY - rect.top) / rect.height - 0.5;
    px.value = x;
    py.value = y;
  });
}

onMounted(() => {
  window.addEventListener('pointermove', handleMove, { passive: true });
});

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', handleMove);
  if (raf) cancelAnimationFrame(raf);
});

const stageStyle = computed(() => ({
  '--aurora-1': palette.value[0],
  '--aurora-2': palette.value[1] ?? palette.value[0],
  '--aurora-3': palette.value[2] ?? palette.value[1] ?? palette.value[0],
  '--aurora-4': palette.value[3] ?? palette.value[0],
  '--mx': px.value.toFixed(3),
  '--my': py.value.toFixed(3)
}) as Record<string, string>);
</script>

<template>
  <div
    ref="stageRef"
    class="aurora-stage"
    :class="[`aurora-stage--${variant}`, { 'aurora-stage--vignette': vignette }]"
    :style="stageStyle"
  >
    <div class="aurora-stage__base" />
    <div class="aurora-stage__orbs">
      <span class="aurora-stage__orb aurora-stage__orb--1" />
      <span class="aurora-stage__orb aurora-stage__orb--2" />
      <span class="aurora-stage__orb aurora-stage__orb--3" />
      <span class="aurora-stage__orb aurora-stage__orb--4" />
    </div>
    <div class="aurora-stage__beams">
      <span class="aurora-stage__beam aurora-stage__beam--a" />
      <span class="aurora-stage__beam aurora-stage__beam--b" />
    </div>
    <div v-if="grid" class="aurora-stage__grid" />
    <div class="aurora-stage__noise" />
    <div v-if="vignette" class="aurora-stage__vignette" />
  </div>
</template>

<style scoped>
.aurora-stage {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  isolation: isolate;
  background: #05060d;
}

.aurora-stage__base {
  position: absolute;
  inset: -10%;
  background:
    radial-gradient(60% 50% at 18% 12%, rgba(124, 92, 255, 0.45) 0%, transparent 60%),
    radial-gradient(55% 50% at 82% 18%, rgba(34, 211, 238, 0.32) 0%, transparent 60%),
    radial-gradient(70% 60% at 70% 95%, rgba(251, 113, 133, 0.30) 0%, transparent 65%),
    radial-gradient(70% 60% at 10% 95%, rgba(52, 211, 153, 0.25) 0%, transparent 65%),
    linear-gradient(180deg, #05060d 0%, #060816 50%, #03040a 100%);
  filter: saturate(1.1);
}

.aurora-stage__orbs,
.aurora-stage__beams,
.aurora-stage__grid,
.aurora-stage__noise,
.aurora-stage__vignette {
  position: absolute;
  inset: 0;
}

/* ---------------- Orbs (large soft blobs) ---------------- */
.aurora-stage__orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.85;
  mix-blend-mode: screen;
  will-change: transform;
}

.aurora-stage__orb--1 {
  width: 60vmax;
  height: 60vmax;
  left: -18vmax;
  top: -18vmax;
  background: radial-gradient(circle at 35% 35%, var(--aurora-1) 0%, transparent 65%);
  animation: orb-drift-1 22s ease-in-out infinite alternate;
  transform: translate3d(calc(var(--mx, 0) * 30px), calc(var(--my, 0) * 30px), 0);
}

.aurora-stage__orb--2 {
  width: 52vmax;
  height: 52vmax;
  right: -20vmax;
  top: -10vmax;
  background: radial-gradient(circle at 50% 50%, var(--aurora-2) 0%, transparent 65%);
  animation: orb-drift-2 26s ease-in-out infinite alternate;
  transform: translate3d(calc(var(--mx, 0) * -36px), calc(var(--my, 0) * 24px), 0);
}

.aurora-stage__orb--3 {
  width: 56vmax;
  height: 56vmax;
  right: -10vmax;
  bottom: -22vmax;
  background: radial-gradient(circle at 50% 50%, var(--aurora-3) 0%, transparent 65%);
  animation: orb-drift-3 28s ease-in-out infinite alternate;
  transform: translate3d(calc(var(--mx, 0) * 24px), calc(var(--my, 0) * -28px), 0);
}

.aurora-stage__orb--4 {
  width: 48vmax;
  height: 48vmax;
  left: -16vmax;
  bottom: -22vmax;
  background: radial-gradient(circle at 50% 50%, var(--aurora-4) 0%, transparent 65%);
  animation: orb-drift-4 30s ease-in-out infinite alternate;
  transform: translate3d(calc(var(--mx, 0) * -22px), calc(var(--my, 0) * -22px), 0);
}

@keyframes orb-drift-1 {
  0%   { transform: translate3d(0, 0, 0) scale(1); }
  100% { transform: translate3d(8vmax, 6vmax, 0) scale(1.15); }
}

@keyframes orb-drift-2 {
  0%   { transform: translate3d(0, 0, 0) scale(1); }
  100% { transform: translate3d(-6vmax, 8vmax, 0) scale(1.18); }
}

@keyframes orb-drift-3 {
  0%   { transform: translate3d(0, 0, 0) scale(1.05); }
  100% { transform: translate3d(-7vmax, -6vmax, 0) scale(0.95); }
}

@keyframes orb-drift-4 {
  0%   { transform: translate3d(0, 0, 0) scale(1); }
  100% { transform: translate3d(7vmax, -8vmax, 0) scale(1.1); }
}

/* ---------------- Conic light beams ---------------- */
.aurora-stage__beam {
  position: absolute;
  width: 130%;
  height: 130%;
  top: -15%;
  left: -15%;
  background: conic-gradient(
    from 90deg at 50% 50%,
    transparent 0deg,
    rgba(124, 92, 255, 0.18) 60deg,
    transparent 120deg,
    rgba(34, 211, 238, 0.14) 180deg,
    transparent 240deg,
    rgba(251, 113, 133, 0.16) 300deg,
    transparent 360deg
  );
  mix-blend-mode: screen;
  filter: blur(40px);
  opacity: 0.5;
  animation: beam-spin 60s linear infinite;
}

.aurora-stage__beam--b {
  animation-direction: reverse;
  animation-duration: 80s;
  opacity: 0.35;
  filter: blur(70px);
}

@keyframes beam-spin {
  to { transform: rotate(360deg); }
}

/* ---------------- Perspective grid ---------------- */
.aurora-stage__grid {
  background-image:
    linear-gradient(to right, rgba(255, 255, 255, 0.06) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(255, 255, 255, 0.06) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(ellipse at 50% 60%, rgba(0, 0, 0, 0.55) 0%, transparent 75%);
  -webkit-mask-image: radial-gradient(ellipse at 50% 60%, rgba(0, 0, 0, 0.55) 0%, transparent 75%);
  opacity: 0.6;
}

/* ---------------- Film grain noise ---------------- */
.aurora-stage__noise {
  background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='220' height='220'><filter id='n'><feTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='2' stitchTiles='stitch'/><feColorMatrix values='0 0 0 0 1  0 0 0 0 1  0 0 0 0 1  0 0 0 0.55 0'/></filter><rect width='100%25' height='100%25' filter='url(%23n)'/></svg>");
  background-size: 220px 220px;
  opacity: 0.07;
  mix-blend-mode: overlay;
}

.aurora-stage__vignette {
  background: radial-gradient(ellipse at 50% 50%, transparent 50%, rgba(2, 4, 12, 0.7) 100%);
}

/* ---------------- Variant tweaks ---------------- */
.aurora-stage--auth .aurora-stage__orb--1 { left: -10vmax; top: -25vmax; }
.aurora-stage--auth .aurora-stage__orb--3 { right: -22vmax; bottom: -10vmax; }
.aurora-stage--panel .aurora-stage__base { filter: saturate(0.9); }
.aurora-stage--panel .aurora-stage__orb { opacity: 0.55; filter: blur(110px); }
</style>

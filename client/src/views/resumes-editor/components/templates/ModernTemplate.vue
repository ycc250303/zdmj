<script setup lang="ts">
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales'; // 引入国际化

interface Props {
  data: ResumeApi.ResumeContentDTO;
}
const props = defineProps<Props>();
</script>

<template>
  <div class="resume-wrapper min-h-[1123px] flex flex-col gap-10 bg-white p-12 text-slate-800 font-sans shadow-sm">
    <header class="text-center">
      <h1 class="theme-text mb-3 text-4xl text-slate-900 font-black tracking-wider">
        {{ props.data.personalInfo?.name || $t('page.resume.name') }}
      </h1>
      <div
        v-if="props.data.personalInfo?.preferredWorkCity"
        class="mb-5 text-base text-slate-700 font-bold tracking-wide"
      >
        {{ props.data.personalInfo.preferredWorkCity }}
      </div>

      <div
        class="flex items-center justify-center gap-4 border-b border-t border-slate-200 py-3 text-sm text-slate-600"
      >
        <span v-if="props.data.personalInfo?.phone">{{ props.data.personalInfo.phone }}</span>
        <span
          v-if="props.data.personalInfo?.phone && props.data.personalInfo?.homepageUrl"
          class="text-slate-300 font-black"
        >
          |
        </span>
        <span v-if="props.data.personalInfo?.homepageUrl">
          <a
            :href="props.data.personalInfo.homepageUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="theme-text hover:text-blue-600"
          >
            {{ props.data.personalInfo.homepageUrl.replace(/^https?:\/\//, '') }}
          </a>
        </span>
      </div>
    </header>

    <section v-if="props.data.educations && props.data.educations.length">
      <h2 class="modern-section-title">{{ $t('page.resume.education') }}</h2>
      <div class="mt-5 flex flex-col gap-5">
        <div v-for="edu in props.data.educations" v-show="edu.visible" :key="edu.id" class="modern-item-grid">
          <div class="col-span-8 pr-4">
            <h3 class="text-lg text-slate-800 font-bold">{{ edu.school }}</h3>
            <p class="mt-1 text-sm text-slate-700 font-medium">{{ edu.major }} / {{ edu.degree }}</p>
          </div>
          <div class="col-span-4 text-right text-sm text-slate-600 font-medium">
            {{ edu.startDate }} - {{ edu.endDate || $t('page.resume.present') }}
            <p v-if="edu.gpa" class="mt-1 text-slate-500 font-bold">{{ $t('page.resume.gpa') }}: {{ edu.gpa }}</p>
          </div>
        </div>
      </div>
    </section>

    <section v-if="props.data.skill && props.data.skill.content && props.data.skill.content.length">
      <h2 class="modern-section-title">{{ $t('page.resume.skills') }}</h2>
      <div class="grid grid-cols-2 mt-5 gap-x-10 gap-y-4 text-sm text-slate-700">
        <div v-for="(item, index) in props.data.skill.content" :key="index">
          <span class="mr-2 text-slate-800 font-black tracking-tight">{{ item.type }}:</span>
          <span>{{ item.content.join('、') }}</span>
        </div>
      </div>
    </section>

    <section v-if="props.data.careers && props.data.careers.length">
      <h2 class="modern-section-title">{{ $t('page.resume.experience') }}</h2>
      <div class="mt-5 flex flex-col gap-8">
        <div v-for="career in props.data.careers" v-show="career.visible" :key="career.id" class="modern-item-grid">
          <div class="col-span-8 pr-4">
            <h3 class="text-lg text-slate-800 font-bold">{{ career.position }}</h3>
            <p class="mt-1 text-sm text-slate-500 font-semibold">{{ career.company }}</p>
            <div
              class="resume-rich-text mt-3 border-t border-slate-100 pt-2 text-sm text-slate-600 leading-relaxed"
              v-html="career.details || '<p></p>'"
            ></div>
          </div>
          <div class="theme-text col-span-4 text-right text-sm font-bold">
            {{ career.startDate }} - {{ career.endDate || $t('page.resume.present') }}
          </div>
        </div>
      </div>
    </section>

    <section v-if="props.data.projects && props.data.projects.length">
      <h2 class="modern-section-title">{{ $t('page.resume.projects') }}</h2>
      <div class="mt-5 flex flex-col gap-8">
        <div v-for="proj in props.data.projects" v-show="proj.visible" :key="proj.id" class="modern-item-grid">
          <div class="col-span-8 pr-4">
            <h3 class="text-lg text-slate-800 font-bold">{{ proj.name }}</h3>
            <p class="mt-1 text-sm text-slate-500 font-semibold">{{ proj.role }}</p>
            <div
              class="resume-rich-text mt-3 border-t border-slate-100 pt-2 text-sm text-slate-600 leading-relaxed"
              v-html="proj.description || '<p></p>'"
            ></div>
          </div>
          <div class="col-span-4 text-right text-sm text-slate-600 font-bold">
            {{ proj.startDate }} - {{ proj.endDate || $t('page.resume.present') }}
          </div>
        </div>
      </div>
    </section>

    <section v-if="props.data.awards && props.data.awards.length">
      <h2 class="modern-section-title">{{ $t('page.resume.awards') }}</h2>
      <div class="mt-5 flex flex-col gap-5">
        <div v-for="award in props.data.awards" :key="award.id" class="modern-item-grid">
          <div class="col-span-8 pr-4">
            <h3 class="text-lg text-slate-800 font-bold">{{ award.name }}</h3>
            <p v-if="award.description" class="mt-1 text-sm text-slate-600">{{ award.description }}</p>
          </div>
          <div class="col-span-4 text-right text-sm text-slate-600 font-medium">
            {{ award.awardDate }}
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* =========== 核心绑定机制：只有文字和极个别元素用主题色 =========== */
.theme-text {
  color: var(--theme-color);
}

/* Bronzor 风格的大字加细分割线样式 */
.modern-section-title {
  font-size: 1.125rem;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.15em;
  color: #1e293b;
  margin-bottom: 0.25rem;
  border-bottom: 2px solid #e2e8f0;
  display: block;
  padding-bottom: 0.5rem;
  position: relative;
}

.modern-section-title::before {
  content: '';
  position: absolute;
  left: 0;
  bottom: -2px;
  width: 40px;
  height: 2px;
  background-color: var(--theme-color);
}

/* 经典的左右布局 Grid：修复了原生 CSS 属性拼写 */
.modern-item-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  align-items: start;
}

/* 富文本样式 */
.resume-rich-text :deep(p) {
  margin-bottom: 0.25rem;
  text-align: justify;
}
.resume-rich-text :deep(ul) {
  list-style-type: none;
  padding-left: 0;
  margin-bottom: 0.25rem;
}
.resume-rich-text :deep(ul li) {
  position: relative;
  padding-left: 1rem;
  margin-bottom: 0.15rem;
}
.resume-rich-text :deep(ul li::before) {
  content: '•';
  color: var(--theme-color);
  position: absolute;
  left: 0;
  font-weight: bold;
}
.resume-rich-text :deep(ol) {
  list-style-type: decimal;
  padding-left: 1.2rem;
  margin-bottom: 0.25rem;
}
.resume-rich-text :deep(b),
.resume-rich-text :deep(strong) {
  font-weight: 700;
  color: #0f172a;
}

@media print {
  .resume-wrapper {
    box-shadow: none !important;
    padding: 2.5rem !important;
  }
  .resume-wrapper section {
    page-break-inside: avoid;
    break-inside: avoid;
    margin-bottom: 2rem !important;
  }
}
</style>

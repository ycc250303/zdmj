<script setup lang="ts">
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales'; // 引入国际化

interface Props {
  data: ResumeApi.ResumeContentDTO;
}
const props = defineProps<Props>();
</script>

<template>
  <div class="resume-wrapper grid grid-cols-12 min-h-[1123px] text-slate-800 font-sans shadow-sm">
    <aside class="col-span-4 flex flex-col gap-8 border-r border-slate-200 bg-[#f8fafc] px-6 py-10">
      <section>
        <h2 class="theme-section-title">{{ $t('page.resume.contact', 'Contact') }}</h2>
        <div class="flex flex-col gap-3 text-sm text-slate-600 font-medium">
          <div v-if="props.data.personalInfo?.phone" class="flex items-center gap-2">
            <span class="text-lg text-slate-400">📞</span>
            <span>{{ props.data.personalInfo.phone }}</span>
          </div>
          <div v-if="props.data.personalInfo?.homepageUrl" class="flex items-center gap-2">
            <span class="text-lg text-slate-400">🔗</span>
            <a
              :href="props.data.personalInfo.homepageUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="truncate break-all hover:text-blue-600"
            >
              {{ props.data.personalInfo.homepageUrl.replace(/^https?:\/\//, '') }}
            </a>
          </div>
          <div v-if="props.data.personalInfo?.preferredWorkCity" class="flex items-center gap-2">
            <span>{{ props.data.personalInfo.preferredWorkCity }}</span>
          </div>
        </div>
      </section>

      <section v-if="props.data.educations && props.data.educations.length">
        <h2 class="theme-section-title">{{ $t('page.resume.education', 'Education') }}</h2>
        <div class="flex flex-col gap-4">
          <div v-for="edu in props.data.educations" v-show="edu.visible" :key="edu.id">
            <h3 class="text-slate-800 font-bold">{{ edu.school }}</h3>
            <div class="mt-0.5 text-sm text-slate-600">{{ edu.major }}</div>
            <div class="mt-1 text-xs text-slate-400 font-medium">
              {{ edu.startDate }} - {{ edu.endDate || $t('page.resume.present', 'Present') }}
            </div>
            <div v-if="edu.gpa" class="mt-1 text-xs text-slate-500 font-bold">GPA: {{ edu.gpa }}</div>
          </div>
        </div>
      </section>

      <section v-if="props.data.skill && props.data.skill.content && props.data.skill.content.length">
        <h2 class="theme-section-title">{{ $t('page.resume.skills', 'Skills') }}</h2>
        <div class="flex flex-col gap-3">
          <div v-for="(item, index) in props.data.skill.content" :key="index">
            <h3 class="mb-1 text-sm text-slate-800 font-bold">{{ item.type }}</h3>
            <div class="mt-1 flex flex-wrap gap-1.5">
              <span
                v-for="skill in item.content"
                :key="skill"
                class="theme-pill rounded-md px-2 py-0.5 text-xs font-semibold"
              >
                {{ skill }}
              </span>
            </div>
          </div>
        </div>
      </section>
    </aside>

    <main class="col-span-8 bg-white px-10 py-12">
      <header class="mb-10">
        <h1 class="mb-2 text-4xl text-slate-900 font-black tracking-tight">
          {{ props.data.personalInfo?.name || $t('page.resume.name', '姓名') }}
        </h1>
        <div v-if="props.data.personalInfo?.preferredWorkCity" class="theme-text text-lg font-medium tracking-wide">
          {{ props.data.personalInfo.preferredWorkCity }}
        </div>
      </header>

      <section v-if="props.data.careers && props.data.careers.length" class="mb-8">
        <div class="mb-4 flex items-center gap-2">
          <span class="theme-text text-2xl">💼</span>
          <h2 class="text-xl text-slate-800 font-black">{{ $t('page.resume.experience', 'Experience') }}</h2>
        </div>

        <div class="relative flex flex-col gap-6">
          <div class="-z-0 absolute bottom-0 left-2 top-2 w-px bg-slate-200"></div>

          <div v-for="career in props.data.careers" v-show="career.visible" :key="career.id" class="relative z-10 pl-6">
            <div
              class="theme-bg absolute left-[5px] top-1.5 h-2 w-2 rounded-full outline-4 outline-white outline"
            ></div>

            <div class="mb-1 flex items-start justify-between">
              <h3 class="text-lg text-slate-800 font-bold leading-tight">{{ career.position }}</h3>
              <span class="theme-date rounded px-2 py-1 text-xs font-bold">
                {{ career.startDate }} - {{ career.endDate || $t('page.resume.present', 'Present') }}
              </span>
            </div>
            <div class="mb-3 text-sm text-slate-500 font-semibold">{{ career.company }}</div>

            <div
              class="resume-rich-text text-sm text-slate-600 leading-relaxed"
              v-html="career.details || '<p></p>'"
            ></div>
          </div>
        </div>
      </section>

      <section v-if="props.data.projects && props.data.projects.length">
        <div class="mb-4 flex items-center gap-2">
          <span class="theme-text text-2xl">🚀</span>
          <h2 class="text-xl text-slate-800 font-black">{{ $t('page.resume.projects', 'Projects') }}</h2>
        </div>

        <div class="relative flex flex-col gap-6">
          <div class="-z-0 absolute bottom-0 left-2 top-2 w-px bg-slate-200"></div>

          <div v-for="proj in props.data.projects" v-show="proj.visible" :key="proj.id" class="relative z-10 pl-6">
            <div
              class="theme-bg absolute left-[5px] top-1.5 h-2 w-2 rounded-full outline-4 outline-white outline"
              style="opacity: 0.7"
            ></div>

            <div class="mb-1 flex items-start justify-between">
              <h3 class="text-lg text-slate-800 font-bold leading-tight">{{ proj.name }}</h3>
              <span class="theme-date rounded px-2 py-1 text-xs font-bold">
                {{ proj.startDate }} - {{ proj.endDate || $t('page.resume.present', 'Present') }}
              </span>
            </div>
            <div class="mb-3 text-sm text-slate-500 font-semibold">{{ proj.role }}</div>

            <div
              class="resume-rich-text text-sm text-slate-600 leading-relaxed"
              v-html="proj.description || '<p></p>'"
            ></div>
          </div>
        </div>
      </section>

      <section v-if="props.data.awards && props.data.awards.length" class="mb-8">
        <div class="mb-4 flex items-center gap-2">
          <h2 class="text-xl text-slate-800 font-black">{{ $t('page.resume.awards') }}</h2>
        </div>
        <div class="flex flex-col gap-4">
          <div v-for="award in props.data.awards" :key="award.id">
            <div class="mb-1 flex items-start justify-between">
              <h3 class="text-lg text-slate-800 font-bold leading-tight">{{ award.name }}</h3>
              <span class="theme-date rounded px-2 py-1 text-xs font-bold">{{ award.awardDate }}</span>
            </div>
            <p v-if="award.description" class="text-sm text-slate-600">{{ award.description }}</p>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
/* =========== 动态主题色核心绑定 =========== */
.theme-text {
  color: var(--theme-color);
}
.theme-bg {
  background-color: var(--theme-color);
}
.theme-section-title {
  font-size: 0.875rem;
  font-weight: 900;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--theme-color);
  margin-bottom: 1rem;
  border-bottom: 2px solid var(--theme-color);
  display: inline-block;
  padding-bottom: 0.25rem;
}
/* 利用 currentColor 和透明度生成胶囊和日期背景 */
.theme-pill {
  color: var(--theme-color);
  background-color: color-mix(in srgb, var(--theme-color) 15%, transparent);
}
.theme-date {
  color: var(--theme-color);
  background-color: color-mix(in srgb, var(--theme-color) 10%, transparent);
}

/* 富文本样式 */
.resume-rich-text :deep(p) {
  margin-bottom: 0.35rem;
  text-align: justify;
}
.resume-rich-text :deep(ul) {
  list-style-type: none;
  padding-left: 0;
  margin-bottom: 0.5rem;
}
.resume-rich-text :deep(ul li) {
  position: relative;
  padding-left: 1.1rem;
  margin-bottom: 0.25rem;
}
.resume-rich-text :deep(ul li::before) {
  content: '•';
  color: var(--theme-color); /* 圆点也变为动态主题色 */
  position: absolute;
  left: 0;
  font-weight: bold;
  font-size: 1.2em;
  line-height: 1;
}
.resume-rich-text :deep(ol) {
  list-style-type: decimal;
  padding-left: 1.2rem;
  margin-bottom: 0.5rem;
}
.resume-rich-text :deep(b),
.resume-rich-text :deep(strong) {
  font-weight: 700;
  color: #0f172a;
}
.resume-rich-text :deep(u) {
  text-decoration-color: #cbd5e1;
  text-underline-offset: 3px;
}

@media print {
  .resume-wrapper {
    box-shadow: none !important;
  }
  .resume-wrapper section,
  .resume-wrapper .relative {
    page-break-inside: avoid;
    break-inside: avoid;
  }
  .preview-container {
    transform: none !important;
  }
}
</style>

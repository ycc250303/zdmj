<script setup lang="ts">
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales'; // 引入国际化

interface Props {
  data: ResumeApi.ResumeContentDTO;
  personal: {
    fullName: string;
    phone: string;
    email: string;
    major: string;
    homepageUrl?: string;
  };
}
const props = defineProps<Props>();
</script>

<template>
  <div class="resume-wrapper grid grid-cols-12 min-h-[1123px] text-slate-800 font-sans shadow-sm">
    
    <aside class="col-span-4 bg-[#f8fafc] px-6 py-10 border-r border-slate-200 flex flex-col gap-8">
      
      <section>
        <h2 class="theme-section-title">{{ $t('page.resume.contact', 'Contact') }}</h2>
        <div class="flex flex-col gap-3 text-sm text-slate-600 font-medium">
          <div v-if="props.personal?.email" class="flex items-center gap-2">
            <div class="i-mdi-email text-lg text-slate-400"></div>
            <span class="break-all">{{ props.personal.email }}</span>
          </div>
          <div v-if="props.personal?.phone" class="flex items-center gap-2">
            <div class="i-mdi-phone text-lg text-slate-400"></div>
            <span>{{ props.personal.phone }}</span>
          </div>
          <div v-if="props.personal?.homepageUrl" class="flex items-center gap-2">
            <div class="i-mdi-link-variant text-lg text-slate-400"></div>
            <a :href="props.personal.homepageUrl" target="_blank" class="break-all hover:text-blue-600 truncate">{{ props.personal.homepageUrl.replace(/^https?:\/\//, '') }}</a>
          </div>
        </div>
      </section>

      <section v-if="props.data.educations && props.data.educations.length">
        <h2 class="theme-section-title">{{ $t('page.resume.education', 'Education') }}</h2>
        <div class="flex flex-col gap-4">
          <div v-for="edu in props.data.educations" :key="edu.id" v-show="edu.visible">
            <h3 class="font-bold text-slate-800">{{ edu.school }}</h3>
            <div class="text-sm text-slate-600 mt-0.5">{{ edu.major }}</div>
            <div class="text-xs text-slate-400 font-medium mt-1">{{ edu.startDate }} - {{ edu.endDate || $t('page.resume.present', 'Present') }}</div>
            <div v-if="edu.gpa" class="text-xs font-bold text-slate-500 mt-1">GPA: {{ edu.gpa }}</div>
          </div>
        </div>
      </section>

      <section v-if="props.data.skill && props.data.skill.content && props.data.skill.content.length">
        <h2 class="theme-section-title">{{ $t('page.resume.skills', 'Skills') }}</h2>
        <div class="flex flex-col gap-3">
          <div v-for="(item, index) in props.data.skill.content" :key="index">
            <h3 class="font-bold text-sm text-slate-800 mb-1">{{ item.type }}</h3>
            <div class="flex flex-wrap gap-1.5 mt-1">
              <span 
                v-for="skill in item.content" 
                :key="skill" 
                class="text-xs font-semibold px-2 py-0.5 rounded-md theme-pill"
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
        <h1 class="text-4xl font-black text-slate-900 tracking-tight mb-2">{{ props.personal?.fullName || $t('page.resume.name', '姓名') }}</h1>
        <div class="text-lg font-medium tracking-wide theme-text">
          {{ props.personal?.major || $t('page.resume.targetJob', '目标岗位') }}
        </div>
      </header>

      <section v-if="props.data.careers && props.data.careers.length" class="mb-8">
        <div class="flex items-center gap-2 mb-4">
          <div class="i-mdi-briefcase text-2xl theme-text"></div>
          <h2 class="text-xl font-black text-slate-800">{{ $t('page.resume.experience', 'Experience') }}</h2>
        </div>
        
        <div class="flex flex-col gap-6 relative">
          <div class="absolute left-2 top-2 bottom-0 w-px bg-slate-200 -z-0"></div>
          
          <div v-for="career in props.data.careers" :key="career.id" v-show="career.visible" class="relative z-10 pl-6">
            <div class="absolute left-[5px] top-1.5 w-2 h-2 rounded-full outline outline-4 outline-white theme-bg"></div>
            
            <div class="flex justify-between items-start mb-1">
              <h3 class="font-bold text-lg text-slate-800 leading-tight">{{ career.position }}</h3>
              <span class="text-xs font-bold px-2 py-1 rounded theme-date">{{ career.startDate }} - {{ career.endDate || $t('page.resume.present', 'Present') }}</span>
            </div>
            <div class="text-sm font-semibold text-slate-500 mb-3">{{ career.company }}</div>
            
            <div class="text-sm text-slate-600 leading-relaxed resume-rich-text" v-html="career.details || '<p></p>'"></div>
          </div>
        </div>
      </section>

      <section v-if="props.data.projects && props.data.projects.length">
        <div class="flex items-center gap-2 mb-4">
          <div class="i-mdi-rocket-launch text-2xl theme-text"></div>
          <h2 class="text-xl font-black text-slate-800">{{ $t('page.resume.projects', 'Projects') }}</h2>
        </div>
        
        <div class="flex flex-col gap-6 relative">
          <div class="absolute left-2 top-2 bottom-0 w-px bg-slate-200 -z-0"></div>

          <div v-for="proj in props.data.projects" :key="proj.id" v-show="proj.visible" class="relative z-10 pl-6">
             <div class="absolute left-[5px] top-1.5 w-2 h-2 rounded-full outline outline-4 outline-white theme-bg" style="opacity: 0.7;"></div>

            <div class="flex justify-between items-start mb-1">
              <h3 class="font-bold text-lg text-slate-800 leading-tight">{{ proj.name }}</h3>
              <span class="text-xs font-bold px-2 py-1 rounded theme-date">{{ proj.startDate }} - {{ proj.endDate || $t('page.resume.present', 'Present') }}</span>
            </div>
            <div class="text-sm font-semibold text-slate-500 mb-3">{{ proj.role }}</div>
            
            <div class="text-sm text-slate-600 leading-relaxed resume-rich-text" v-html="proj.description || '<p></p>'"></div>
          </div>
        </div>
      </section>

    </main>
  </div>
</template>

<style scoped>
/* =========== 动态主题色核心绑定 =========== */
.theme-text { color: var(--theme-color); }
.theme-bg { background-color: var(--theme-color); }
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
.resume-rich-text :deep(p) { margin-bottom: 0.35rem; text-align: justify; }
.resume-rich-text :deep(ul) { list-style-type: none; padding-left: 0; margin-bottom: 0.5rem; }
.resume-rich-text :deep(ul li) { position: relative; padding-left: 1.1rem; margin-bottom: 0.25rem; }
.resume-rich-text :deep(ul li::before) {
  content: "•";
  color: var(--theme-color); /* 圆点也变为动态主题色 */
  position: absolute;
  left: 0;
  font-weight: bold;
  font-size: 1.2em;
  line-height: 1;
}
.resume-rich-text :deep(ol) { list-style-type: decimal; padding-left: 1.2rem; margin-bottom: 0.5rem; }
.resume-rich-text :deep(b), .resume-rich-text :deep(strong) { font-weight: 700; color: #0f172a; }
.resume-rich-text :deep(u) { text-decoration-color: #cbd5e1; text-underline-offset: 3px; }

@media print {
  .resume-wrapper { box-shadow: none !important; }
  .resume-wrapper section, .resume-wrapper .relative { page-break-inside: avoid; break-inside: avoid; }
  .preview-container { transform: none !important; }
}
</style>
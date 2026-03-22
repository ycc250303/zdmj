<script setup lang="ts">
import { useResumeStore } from '@/store/modules/resumeStore';
import RichTextEditor from './RichTextEditor.vue';
import { $t } from '@/locales';

const resumeStore = useResumeStore();

async function handleSaveResume() {
  await resumeStore.saveAllData();
}
</script>

<template>
  <div class="h-full flex flex-col bg-white">
    <div class="p-4 border-b border-slate-200 flex justify-between items-center bg-slate-50 sticky top-0 z-10 shadow-sm">
      <h2 class="font-bold text-slate-800">{{ $t('page.resume.manageContent') }}</h2>
      <NButton type="primary" size="medium" :loading="resumeStore.isSaving" @click="handleSaveResume">
        {{ $t('page.resume.saveSync') }}
      </NButton>
    </div>

    <div class="flex-1 overflow-y-auto p-4" v-if="resumeStore.resumeData">
      <NCollapse :default-expanded-names="['basic', 'projects']" accordion>
        
        <NCollapseItem :title="$t('page.resume.basicInfo')" name="basic">
          <NGrid :x-gap="12" :cols="2">
            <NFormItem :label="$t('page.resume.fullName')">
              <NInput v-model:value="resumeStore.personalInfo.fullName" />
            </NFormItem>
            <NFormItem :label="$t('page.resume.major')">
              <NInput v-model:value="resumeStore.personalInfo.major" />
            </NFormItem>
            <NFormItem :label="$t('page.resume.phone')">
              <NInput v-model:value="resumeStore.personalInfo.phone" />
            </NFormItem>
            <NFormItem :label="$t('page.resume.email')">
              <NInput v-model:value="resumeStore.personalInfo.email" />
            </NFormItem>
          </NGrid>
          <NFormItem :label="$t('page.profile.basicInfo.homepageUrl', '个人主页 / Github 等')">
            <NInput v-model:value="resumeStore.personalInfo.homepageUrl" placeholder="https://" />
          </NFormItem>
        </NCollapseItem>

        <NCollapseItem :title="$t('page.resume.education')" name="educations">
          <template v-if="resumeStore.resumeData.educations && resumeStore.resumeData.educations.length > 0">
          <div v-for="(edu, index) in resumeStore.resumeData.educations" :key="edu.id" class="mb-6 pb-6 border-b border-slate-100 last:border-0">
            <div class="flex justify-between items-center mb-4">
              <span class="font-bold text-slate-700">{{ edu.school || `${$t('page.resume.education')} ${index + 1}` }}</span>
              <NSwitch v-model:value="edu.visible" size="small" />
            </div>
            <NGrid :x-gap="12" :cols="2">
              <NFormItem :label="$t('page.resume.school')"><NInput v-model:value="edu.school" /></NFormItem>
              <NFormItem :label="$t('page.resume.major')"><NInput v-model:value="edu.major" /></NFormItem>
              <NFormItem :label="$t('page.resume.startDate')"><NInput v-model:value="edu.startDate" /></NFormItem>
              <NFormItem :label="$t('page.resume.endDate')"><NInput v-model:value="edu.endDate" /></NFormItem>
              <NFormItem :label="$t('page.resume.gpa')"><NInput v-model:value="edu.gpa" /></NFormItem>
            </NGrid>
          </div>
          </template>

          <div v-else class="py-6 flex flex-col items-center justify-center text-slate-400 bg-slate-50/50 rounded-lg border border-dashed border-slate-200">
            <div class="i-mdi-text-box-remove-outline text-3xl mb-2 text-slate-300"></div>
            <p class="text-sm">暂无教育经历数据</p>
            <p class="text-xs mt-1">请前往左侧菜单的“个人信息管理”中添加</p>
          </div>
        </NCollapseItem>

        <NCollapseItem :title="$t('page.resume.projects')" name="projects">
          <template v-if="resumeStore.resumeData.projects && resumeStore.resumeData.projects.length > 0">
          <div v-for="(proj, index) in resumeStore.resumeData.projects" :key="proj.id" class="mb-6 pb-6 border-b border-slate-100 last:border-0">
            <div class="flex justify-between items-center mb-4">
              <span class="font-bold text-slate-700">{{ proj.name || `${$t('page.resume.projects')} ${index + 1}` }}</span>
              <NSwitch v-model:value="proj.visible" size="small" /> 
            </div>
            <NFormItem :label="$t('page.resume.projectName')"><NInput v-model:value="proj.name" /></NFormItem>
            <NGrid :x-gap="12" :cols="2">
              <NFormItem :label="$t('page.resume.role')"><NInput v-model:value="proj.role" /></NFormItem>
              <NFormItem :label="$t('page.resume.duration')"><NInput v-model:value="proj.startDate" /></NFormItem>
            </NGrid>
            <NFormItem :label="$t('page.resume.projectDesc')">
              <RichTextEditor v-model="proj.description" />
            </NFormItem>
          </div>
          </template>

          <div v-else class="py-6 flex flex-col items-center justify-center text-slate-400 bg-slate-50/50 rounded-lg border border-dashed border-slate-200">
            <div class="i-mdi-text-box-remove-outline text-3xl mb-2 text-slate-300"></div>
            <p class="text-sm">暂无项目经历数据</p>
            <p class="text-xs mt-1">请前往左侧菜单的“个人信息管理”中添加</p>
          </div>
        </NCollapseItem>

        <NCollapseItem :title="$t('page.resume.experience')" name="careers">
          <template v-if="resumeStore.resumeData.careers && resumeStore.resumeData.careers.length > 0">
          <div v-for="(career, index) in resumeStore.resumeData.careers" :key="career.id" class="mb-6 pb-6 border-b border-slate-100 last:border-0">
            <div class="flex justify-between items-center mb-4">
              <span class="font-bold text-slate-700">{{ career.company || `${$t('page.resume.experience')} ${index + 1}` }}</span>
              <NSwitch v-model:value="career.visible" size="small" />
            </div>
            <NFormItem :label="$t('page.resume.company')"><NInput v-model:value="career.company" /></NFormItem>
            <NFormItem :label="$t('page.resume.jobDetails')">
              <RichTextEditor v-model="career.details" />
            </NFormItem>
          </div>
          </template>

          <div v-else class="py-6 flex flex-col items-center justify-center text-slate-400 bg-slate-50/50 rounded-lg border border-dashed border-slate-200">
            <div class="i-mdi-text-box-remove-outline text-3xl mb-2 text-slate-300"></div>
            <p class="text-sm">暂无实习经历数据</p>
            <p class="text-xs mt-1">请前往左侧菜单的“个人信息管理”中添加</p>
          </div>

        </NCollapseItem>

        <NCollapseItem :title="$t('page.resume.skills')" name="skills">
           <div class="mb-4">
             <NFormItem :label="$t('page.resume.skillListName')">
               <NInput v-model:value="resumeStore.resumeData.skill.name" />
             </NFormItem>
             <NAlert type="info" class="mt-2">{{ $t('page.resume.skillTip') }}</NAlert>
           </div>
        </NCollapseItem>

      </NCollapse>
    </div>
  </div>
</template>
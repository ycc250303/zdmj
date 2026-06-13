<script setup lang="ts">
import { ref, computed } from 'vue';
import { useResumeStore } from '@/store/modules/resumeStore';
import type { ResumeApi } from '@/service/api/resume';
import RichTextEditor from './RichTextEditor.vue';
import { $t } from '@/locales';

const resumeStore = useResumeStore();

async function handleSaveResume() {
  await resumeStore.saveAllData();
}

// ---- 日期工具：NDatePicker 用 timestamp，API 需要 YYYY-MM-DD ----
function dateStrToTs(str: string | undefined): number | null {
  if (!str) return null;
  const d = new Date(str);
  return isNaN(d.getTime()) ? null : d.getTime();
}
function tsToDateStr(ts: number | null): string {
  if (!ts) return '';
  const d = new Date(ts);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

// ---- 新增教育经历 ----
const showAddEdu = ref(false);
const addingEdu = ref(false);
const newEdu = ref<ResumeApi.EducationCreate>({ school: '', major: '', degree: 3, startDate: '', visible: true });
const newEduStartTs = computed({
  get: () => dateStrToTs(newEdu.value.startDate),
  set: (v) => { newEdu.value.startDate = tsToDateStr(v); }
});
const newEduEndTs = computed({
  get: () => dateStrToTs(newEdu.value.endDate),
  set: (v) => { newEdu.value.endDate = tsToDateStr(v) || undefined; }
});
const degreeOptions = computed(() => [
  { label: $t('page.profile.education.degrees.phd'), value: 1 },
  { label: $t('page.profile.education.degrees.master'), value: 2 },
  { label: $t('page.profile.education.degrees.bachelor'), value: 3 },
  { label: $t('page.profile.education.degrees.associate'), value: 4 },
  { label: $t('page.profile.education.degrees.highSchool'), value: 5 },
  { label: $t('page.profile.education.degrees.other'), value: 6 }
]);
async function submitAddEdu() {
  if (!newEdu.value.school || !newEdu.value.startDate) {
    window.$message?.warning($t('page.profile.education.school') + ' / ' + $t('page.resume.startDateLabel'));
    return;
  }
  addingEdu.value = true;
  const ok = await resumeStore.addEducation({ ...newEdu.value });
  addingEdu.value = false;
  if (ok) {
    showAddEdu.value = false;
    newEdu.value = { school: '', major: '', degree: 3, startDate: '', visible: true };
    window.$message?.success($t('page.profile.education.addSuccess'));
  }
}

// ---- 新增项目经历 ----
const showAddProj = ref(false);
const addingProj = ref(false);
const newProj = ref<ResumeApi.ProjectCreate>({ name: '', role: '', startDate: '', description: '', contribution: '', visible: true });
const newProjStartTs = computed({
  get: () => dateStrToTs(newProj.value.startDate),
  set: (v) => { newProj.value.startDate = tsToDateStr(v); }
});
const newProjEndTs = computed({
  get: () => dateStrToTs(newProj.value.endDate),
  set: (v) => { newProj.value.endDate = tsToDateStr(v) || undefined; }
});
async function submitAddProj() {
  if (!newProj.value.name || !newProj.value.startDate) {
    window.$message?.warning($t('page.profile.project.name') + ' / ' + $t('page.resume.startDateLabel'));
    return;
  }
  addingProj.value = true;
  const ok = await resumeStore.addProject({ ...newProj.value });
  addingProj.value = false;
  if (ok) {
    showAddProj.value = false;
    newProj.value = { name: '', role: '', startDate: '', description: '', contribution: '', visible: true };
    window.$message?.success($t('page.profile.project.addSuccess'));
  }
}

// ---- 新增实习经历 ----
const showAddCareer = ref(false);
const addingCareer = ref(false);
const newCareer = ref<ResumeApi.CareerCreate>({ company: '', position: '', startDate: '', visible: true });
const newCareerStartTs = computed({
  get: () => dateStrToTs(newCareer.value.startDate),
  set: (v) => { newCareer.value.startDate = tsToDateStr(v); }
});
const newCareerEndTs = computed({
  get: () => dateStrToTs(newCareer.value.endDate),
  set: (v) => { newCareer.value.endDate = tsToDateStr(v) || undefined; }
});
async function submitAddCareer() {
  if (!newCareer.value.company || !newCareer.value.startDate) {
    window.$message?.warning($t('page.profile.career.company') + ' / ' + $t('page.resume.startDateLabel'));
    return;
  }
  addingCareer.value = true;
  const ok = await resumeStore.addCareer({ ...newCareer.value });
  addingCareer.value = false;
  if (ok) {
    showAddCareer.value = false;
    newCareer.value = { company: '', position: '', startDate: '', visible: true };
    window.$message?.success($t('page.profile.career.addSuccess'));
  }
}

// ---- 删除确认 ----
async function handleDeleteEdu(id: number) {
  const ok = await resumeStore.deleteEducation(id);
  if (ok) window.$message?.success($t('page.profile.education.deleteSuccess'));
}
async function handleDeleteProj(id: number) {
  const ok = await resumeStore.deleteProject(id);
  if (ok) window.$message?.success($t('page.profile.project.deleteSuccess'));
}
async function handleDeleteCareer(id: number) {
  const ok = await resumeStore.deleteCareer(id);
  if (ok) window.$message?.success($t('page.profile.career.deleteSuccess'));
}

// ---- 现有条目的日期绑定辅助 ----
function eduStartTs(edu: ResumeApi.EducationDTO) {
  return computed({
    get: () => dateStrToTs(edu.startDate),
    set: (v: number | null) => { edu.startDate = tsToDateStr(v); }
  });
}
function eduEndTs(edu: ResumeApi.EducationDTO) {
  return computed({
    get: () => dateStrToTs(edu.endDate),
    set: (v: number | null) => { edu.endDate = tsToDateStr(v) || undefined; }
  });
}
function projStartTs(proj: ResumeApi.ProjectDTO) {
  return computed({
    get: () => dateStrToTs(proj.startDate),
    set: (v: number | null) => { proj.startDate = tsToDateStr(v); }
  });
}
function projEndTs(proj: ResumeApi.ProjectDTO) {
  return computed({
    get: () => dateStrToTs(proj.endDate),
    set: (v: number | null) => { proj.endDate = tsToDateStr(v) || undefined; }
  });
}
function careerStartTs(career: ResumeApi.CareerDTO) {
  return computed({
    get: () => dateStrToTs(career.startDate),
    set: (v: number | null) => { career.startDate = tsToDateStr(v); }
  });
}
function careerEndTs(career: ResumeApi.CareerDTO) {
  return computed({
    get: () => dateStrToTs(career.endDate),
    set: (v: number | null) => { career.endDate = tsToDateStr(v) || undefined; }
  });
}
</script>

<template>
  <div class="h-full flex flex-col bg-white dark:bg-dark-200">
    <div class="p-4 border-b border-slate-200 dark:border-gray-700 flex justify-between items-center bg-slate-50 dark:bg-dark-100 sticky top-0 z-10 shadow-sm">
      <h2 class="font-bold text-slate-800 dark:text-gray-200">{{ $t('page.resume.manageContent') }}</h2>
      <NButton type="primary" size="medium" :loading="resumeStore.isSaving" @click="handleSaveResume">
        {{ $t('page.resume.saveSync') }}
      </NButton>
    </div>

    <div class="flex-1 overflow-y-auto p-4" v-if="resumeStore.resumeData">
      <NCollapse :default-expanded-names="['basic', 'projects']" accordion>

        <!-- 基本信息 -->
        <NCollapseItem :title="$t('page.resume.basicInfo')" name="basic">
          <NGrid :x-gap="12" :cols="2">
            <NGridItem>
              <NFormItem :label="$t('page.resume.fullName')">
                <NInput v-model:value="resumeStore.personalInfo.fullName" />
              </NFormItem>
            </NGridItem>
            <NGridItem>
              <NFormItem :label="$t('page.resume.targetJob', '目标岗位/专业')">
                <NInput v-model:value="resumeStore.personalInfo.major" />
              </NFormItem>
            </NGridItem>
            <NGridItem>
              <NFormItem :label="$t('page.resume.phone')">
                <NInput v-model:value="resumeStore.personalInfo.phone" />
              </NFormItem>
            </NGridItem>
            <NGridItem>
              <NFormItem :label="$t('page.resume.email')">
                <NInput v-model:value="resumeStore.personalInfo.email" />
              </NFormItem>
            </NGridItem>
          </NGrid>
          <NFormItem :label="$t('page.profile.basicInfo.homepageUrl', '个人主页 / Github 等')">
            <NInput v-model:value="resumeStore.personalInfo.homepageUrl" placeholder="https://" />
          </NFormItem>
        </NCollapseItem>

        <!-- 教育经历 -->
        <NCollapseItem :title="$t('page.resume.education')" name="educations">
          <template v-if="resumeStore.resumeData.educations && resumeStore.resumeData.educations.length > 0">
            <div v-for="(edu, index) in resumeStore.resumeData.educations" :key="edu.id" class="mb-6 pb-6 border-b border-slate-100 dark:border-gray-700 last:border-0">
              <div class="flex justify-between items-center mb-4">
                <span class="font-bold text-slate-700 dark:text-gray-300">{{ edu.school || `${$t('page.resume.education')} ${index + 1}` }}</span>
                <div class="flex items-center gap-2">
                  <NSwitch v-model:value="edu.visible" size="small" />
                  <NPopconfirm @positive-click="handleDeleteEdu(edu.id)">
                    <template #trigger>
                      <NButton size="tiny" quaternary type="error">{{ $t('page.resume.delete') }}</NButton>
                    </template>
                    {{ $t('page.resume.confirmDeleteEdu') }}
                  </NPopconfirm>
                </div>
              </div>
              <NGrid :x-gap="12" :cols="2">
                <NGridItem>
                  <NFormItem :label="$t('page.resume.school')"><NInput v-model:value="edu.school" /></NFormItem>
                </NGridItem>
                <NGridItem>
                  <NFormItem :label="$t('page.resume.major')"><NInput v-model:value="edu.major" /></NFormItem>
                </NGridItem>
                <NGridItem>
                  <NFormItem :label="$t('page.resume.startDate')">
                    <NDatePicker :value="eduStartTs(edu).value" @update:value="eduStartTs(edu).value = $event" type="date" value-format="yyyy-MM-dd" clearable class="w-full" />
                  </NFormItem>
                </NGridItem>
                <NGridItem>
                  <NFormItem :label="$t('page.resume.endDate')">
                    <NDatePicker :value="eduEndTs(edu).value" @update:value="eduEndTs(edu).value = $event" type="date" value-format="yyyy-MM-dd" clearable class="w-full" />
                  </NFormItem>
                </NGridItem>
                <NGridItem>
                  <NFormItem :label="$t('page.resume.gpa')"><NInput v-model:value="edu.gpa" /></NFormItem>
                </NGridItem>
              </NGrid>
            </div>
          </template>

          <!-- 新增教育经历表单 -->
          <div v-if="showAddEdu" class="mb-4 p-4 bg-slate-50 dark:bg-dark-100 rounded-lg border border-slate-200 dark:border-gray-700">
            <p class="font-semibold text-slate-700 dark:text-gray-300 mb-3">{{ $t('page.resume.addEducation') }}</p>
            <NGrid :x-gap="12" :cols="2">
              <NGridItem>
                <NFormItem :label="$t('page.resume.schoolLabel')"><NInput v-model:value="newEdu.school" :placeholder="$t('page.profile.education.schoolPlaceholder')" /></NFormItem>
              </NGridItem>
              <NGridItem>
                <NFormItem :label="$t('page.resume.majorLabel')"><NInput v-model:value="newEdu.major" :placeholder="$t('page.profile.education.majorPlaceholder')" /></NFormItem>
              </NGridItem>
              <NGridItem>
                <NFormItem :label="$t('page.resume.degree')">
                  <NSelect v-model:value="newEdu.degree" :options="degreeOptions" />
                </NFormItem>
              </NGridItem>
              <NGridItem>
                <NFormItem :label="$t('page.resume.gpaLabel')"><NInput v-model:value="newEdu.gpa" :placeholder="$t('page.profile.education.gpaPlaceholder')" /></NFormItem>
              </NGridItem>
              <NGridItem>
                <NFormItem :label="$t('page.resume.startDateLabel')">
                  <NDatePicker v-model:value="newEduStartTs" type="date" value-format="yyyy-MM-dd" clearable class="w-full" />
                </NFormItem>
              </NGridItem>
              <NGridItem>
                <NFormItem :label="$t('page.resume.endDateLabel')">
                  <NDatePicker v-model:value="newEduEndTs" type="date" value-format="yyyy-MM-dd" clearable class="w-full" />
                </NFormItem>
              </NGridItem>
            </NGrid>
            <div class="flex gap-2 mt-2">
              <NButton size="small" type="primary" :loading="addingEdu" @click="submitAddEdu">{{ $t('page.resume.confirmAdd') }}</NButton>
              <NButton size="small" @click="showAddEdu = false">{{ $t('page.resume.cancel') }}</NButton>
            </div>
          </div>

          <NButton v-if="!showAddEdu" dashed size="small" class="w-full mt-2" @click="showAddEdu = true">+ {{ $t('page.resume.addEducation') }}</NButton>
        </NCollapseItem>

        <!-- 项目经历 -->
        <NCollapseItem :title="$t('page.resume.projects')" name="projects">
          <template v-if="resumeStore.resumeData.projects && resumeStore.resumeData.projects.length > 0">
            <div v-for="(proj, index) in resumeStore.resumeData.projects" :key="proj.id" class="mb-6 pb-6 border-b border-slate-100 dark:border-gray-700 last:border-0">
              <div class="flex justify-between items-center mb-4">
                <span class="font-bold text-slate-700 dark:text-gray-300">{{ proj.name || `${$t('page.resume.projects')} ${index + 1}` }}</span>
                <div class="flex items-center gap-2">
                  <NSwitch v-model:value="proj.visible" size="small" />
                  <NPopconfirm @positive-click="handleDeleteProj(proj.id)">
                    <template #trigger>
                      <NButton size="tiny" quaternary type="error">{{ $t('page.resume.delete') }}</NButton>
                    </template>
                    {{ $t('page.resume.confirmDeleteProj') }}
                  </NPopconfirm>
                </div>
              </div>
              <NFormItem :label="$t('page.resume.projectName')"><NInput v-model:value="proj.name" /></NFormItem>
              <NGrid :x-gap="12" :cols="2">
                <NGridItem>
                  <NFormItem :label="$t('page.resume.role')"><NInput v-model:value="proj.role" /></NFormItem>
                </NGridItem>
                <NGridItem>
                  <NFormItem :label="$t('page.resume.startDate')">
                    <NDatePicker :value="projStartTs(proj).value" @update:value="projStartTs(proj).value = $event" type="date" value-format="yyyy-MM-dd" clearable class="w-full" />
                  </NFormItem>
                </NGridItem>
                <NGridItem>
                  <NFormItem :label="$t('page.resume.endDate')">
                    <NDatePicker :value="projEndTs(proj).value" @update:value="projEndTs(proj).value = $event" type="date" value-format="yyyy-MM-dd" clearable class="w-full" />
                  </NFormItem>
                </NGridItem>
              </NGrid>
              <NFormItem :label="$t('page.resume.projectDesc')">
                <RichTextEditor v-model="proj.description" />
              </NFormItem>
            </div>
          </template>

          <!-- 新增项目经历表单 -->
          <div v-if="showAddProj" class="mb-4 p-4 bg-slate-50 dark:bg-dark-100 rounded-lg border border-slate-200 dark:border-gray-700">
            <p class="font-semibold text-slate-700 dark:text-gray-300 mb-3">{{ $t('page.resume.addProject') }}</p>
            <NFormItem :label="$t('page.resume.projectNameLabel')"><NInput v-model:value="newProj.name" :placeholder="$t('page.profile.project.namePlaceholder')" /></NFormItem>
            <NFormItem :label="$t('page.resume.roleLabel')"><NInput v-model:value="newProj.role" :placeholder="$t('page.profile.project.rolePlaceholder')" /></NFormItem>
            <NGrid :x-gap="12" :cols="2">
              <NGridItem>
                <NFormItem :label="$t('page.resume.startDateLabel')">
                  <NDatePicker v-model:value="newProjStartTs" type="date" value-format="yyyy-MM-dd" clearable class="w-full" />
                </NFormItem>
              </NGridItem>
              <NGridItem>
                <NFormItem :label="$t('page.resume.endDateLabel')">
                  <NDatePicker v-model:value="newProjEndTs" type="date" value-format="yyyy-MM-dd" clearable class="w-full" />
                </NFormItem>
              </NGridItem>
            </NGrid>
            <NFormItem :label="$t('page.resume.descLabel')"><NInput v-model:value="newProj.description" type="textarea" :autosize="{ minRows: 2 }" :placeholder="$t('page.profile.project.descPlaceholder')" /></NFormItem>
            <NFormItem :label="$t('page.resume.contributionLabel')"><NInput v-model:value="newProj.contribution" type="textarea" :autosize="{ minRows: 2 }" :placeholder="$t('page.profile.project.contriPlaceholder')" /></NFormItem>
            <div class="flex gap-2 mt-2">
              <NButton size="small" type="primary" :loading="addingProj" @click="submitAddProj">{{ $t('page.resume.confirmAdd') }}</NButton>
              <NButton size="small" @click="showAddProj = false">{{ $t('page.resume.cancel') }}</NButton>
            </div>
          </div>

          <NButton v-if="!showAddProj" dashed size="small" class="w-full mt-2" @click="showAddProj = true">+ {{ $t('page.resume.addProject') }}</NButton>
        </NCollapseItem>

        <!-- 实习经历 -->
        <NCollapseItem :title="$t('page.resume.experience')" name="careers">
          <template v-if="resumeStore.resumeData.careers && resumeStore.resumeData.careers.length > 0">
            <div v-for="(career, index) in resumeStore.resumeData.careers" :key="career.id" class="mb-6 pb-6 border-b border-slate-100 dark:border-gray-700 last:border-0">
              <div class="flex justify-between items-center mb-4">
                <span class="font-bold text-slate-700 dark:text-gray-300">{{ career.company || `${$t('page.resume.experience')} ${index + 1}` }}</span>
                <div class="flex items-center gap-2">
                  <NSwitch v-model:value="career.visible" size="small" />
                  <NPopconfirm @positive-click="handleDeleteCareer(career.id)">
                    <template #trigger>
                      <NButton size="tiny" quaternary type="error">{{ $t('page.resume.delete') }}</NButton>
                    </template>
                    {{ $t('page.resume.confirmDeleteCareer') }}
                  </NPopconfirm>
                </div>
              </div>
              <NFormItem :label="$t('page.resume.company')"><NInput v-model:value="career.company" /></NFormItem>
              <NGrid :x-gap="12" :cols="2">
                <NGridItem>
                  <NFormItem :label="$t('page.resume.position')"><NInput v-model:value="career.position" /></NFormItem>
                </NGridItem>
                <NGridItem>
                  <NFormItem :label="$t('page.resume.startDate')">
                    <NDatePicker :value="careerStartTs(career).value" @update:value="careerStartTs(career).value = $event" type="date" value-format="yyyy-MM-dd" clearable class="w-full" />
                  </NFormItem>
                </NGridItem>
                <NGridItem>
                  <NFormItem :label="$t('page.resume.endDate')">
                    <NDatePicker :value="careerEndTs(career).value" @update:value="careerEndTs(career).value = $event" type="date" value-format="yyyy-MM-dd" clearable class="w-full" />
                  </NFormItem>
                </NGridItem>
              </NGrid>
              <NFormItem :label="$t('page.resume.jobDetails')">
                <RichTextEditor v-model="career.details" />
              </NFormItem>
            </div>
          </template>

          <!-- 新增实习经历表单 -->
          <div v-if="showAddCareer" class="mb-4 p-4 bg-slate-50 dark:bg-dark-100 rounded-lg border border-slate-200 dark:border-gray-700">
            <p class="font-semibold text-slate-700 dark:text-gray-300 mb-3">{{ $t('page.resume.addCareer') }}</p>
            <NGrid :x-gap="12" :cols="2">
              <NGridItem>
                <NFormItem :label="$t('page.resume.companyLabel')"><NInput v-model:value="newCareer.company" :placeholder="$t('page.profile.career.companyPlaceholder')" /></NFormItem>
              </NGridItem>
              <NGridItem>
                <NFormItem :label="$t('page.resume.positionLabel')"><NInput v-model:value="newCareer.position" :placeholder="$t('page.profile.career.positionPlaceholder')" /></NFormItem>
              </NGridItem>
              <NGridItem>
                <NFormItem :label="$t('page.resume.startDateLabel')">
                  <NDatePicker v-model:value="newCareerStartTs" type="date" value-format="yyyy-MM-dd" clearable class="w-full" />
                </NFormItem>
              </NGridItem>
              <NGridItem>
                <NFormItem :label="$t('page.resume.endDateLabel')">
                  <NDatePicker v-model:value="newCareerEndTs" type="date" value-format="yyyy-MM-dd" clearable class="w-full" />
                </NFormItem>
              </NGridItem>
            </NGrid>
            <NFormItem :label="$t('page.resume.detailsLabel')"><NInput v-model:value="newCareer.details" type="textarea" :autosize="{ minRows: 2 }" :placeholder="$t('page.profile.career.detailsPlaceholder')" /></NFormItem>
            <div class="flex gap-2 mt-2">
              <NButton size="small" type="primary" :loading="addingCareer" @click="submitAddCareer">{{ $t('page.resume.confirmAdd') }}</NButton>
              <NButton size="small" @click="showAddCareer = false">{{ $t('page.resume.cancel') }}</NButton>
            </div>
          </div>

          <NButton v-if="!showAddCareer" dashed size="small" class="w-full mt-2" @click="showAddCareer = true">+ {{ $t('page.resume.addCareer') }}</NButton>
        </NCollapseItem>

        <!-- 专业技能 -->
        <NCollapseItem :title="$t('page.resume.skills')" name="skills">
          <NAlert type="info">{{ $t('page.resume.skillTip') }}</NAlert>
        </NCollapseItem>

      </NCollapse>
    </div>
  </div>
</template>

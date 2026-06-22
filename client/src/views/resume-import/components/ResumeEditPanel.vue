<script setup lang="ts">
import { computed } from 'vue';
import { $t } from '@/locales';
import type { ResumeContentDraft, DraftCareer, DraftEducation, DraftProject, DraftAward } from '../utils/resumeDraft';
import {
  createEmptyAward,
  createEmptyCareer,
  createEmptyEducation,
  createEmptyProject
} from '../utils/resumeDraft';

defineOptions({ name: 'ResumeEditPanel' });

const draft = defineModel<ResumeContentDraft>('draft', { required: true });

const degreeOptions = computed(() => [
  { label: $t('page.profile.education.degrees.phd'), value: 1 },
  { label: $t('page.profile.education.degrees.master'), value: 2 },
  { label: $t('page.profile.education.degrees.bachelor'), value: 3 },
  { label: $t('page.profile.education.degrees.associate'), value: 4 },
  { label: $t('page.profile.education.degrees.highSchool'), value: 5 },
  { label: $t('page.profile.education.degrees.other'), value: 6 }
]);

const awardTypeOptions = computed(() => [
  { label: $t('page.resume.awardTypeScholarship'), value: 1 },
  { label: $t('page.resume.awardTypeCompetition'), value: 2 },
  { label: $t('page.resume.awardTypeOther'), value: 3 }
]);

function dateStrToTs(str: string | undefined): number | null {
  if (!str) return null;
  const d = new Date(str);
  return Number.isNaN(d.getTime()) ? null : d.getTime();
}

function tsToDateStr(ts: number | null): string {
  if (!ts) return '';
  const d = new Date(ts);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function bindDate(item: { startDate?: string; endDate?: string; awardDate?: string }, field: 'startDate' | 'endDate' | 'awardDate') {
  return computed({
    get: () => dateStrToTs(item[field]),
    set: (v: number | null) => {
      item[field] = tsToDateStr(v) || undefined;
    }
  });
}

function removeEducation(index: number) { draft.value.educations.splice(index, 1); }
function removeCareer(index: number) { draft.value.careers.splice(index, 1); }
function removeProject(index: number) { draft.value.projects.splice(index, 1); }
function removeAward(index: number) { draft.value.awards.splice(index, 1); }
function addEducation() { draft.value.educations.push(createEmptyEducation()); }
function addCareer() { draft.value.careers.push(createEmptyCareer()); }
function addProject() { draft.value.projects.push(createEmptyProject()); }
function addAward() { draft.value.awards.push(createEmptyAward()); }
function addSkillGroup() { draft.value.skill.content.push({ type: '', content: [] }); }
function removeSkillGroup(index: number) { draft.value.skill.content.splice(index, 1); }

function eduStart(item: DraftEducation) { return bindDate(item, 'startDate'); }
function eduEnd(item: DraftEducation) { return bindDate(item, 'endDate'); }
function careerStart(item: DraftCareer) { return bindDate(item, 'startDate'); }
function careerEnd(item: DraftCareer) { return bindDate(item, 'endDate'); }
function projectStart(item: DraftProject) { return bindDate(item, 'startDate'); }
function projectEnd(item: DraftProject) { return bindDate(item, 'endDate'); }
function awardDateBind(item: DraftAward) { return bindDate(item, 'awardDate'); }
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <NAlert type="info" class="mb-4" :show-icon="false">
      {{ $t('page.resumeImport.editHint') }}
    </NAlert>
    <NCard class="rounded-2xl shadow-sm">
      <NCollapse :default-expanded-names="['basicInfo', 'education', 'career', 'project', 'award', 'skill']">
        <NCollapseItem :title="$t('page.profile.basicInfo.title')" name="basicInfo">
          <NGrid :cols="2" :x-gap="12">
            <NGridItem><NFormItem :label="$t('page.profile.basicInfo.name')"><NInput v-model:value="draft.personalInfo.name" /></NFormItem></NGridItem>
            <NGridItem><NFormItem :label="$t('page.profile.basicInfo.phone')"><NInput v-model:value="draft.personalInfo.phone" /></NFormItem></NGridItem>
            <NGridItem :span="2"><NFormItem :label="$t('page.profile.basicInfo.homepageUrl')"><NInput v-model:value="draft.personalInfo.homepageUrl" /></NFormItem></NGridItem>
            <NGridItem :span="2"><NFormItem :label="$t('page.profile.basicInfo.preferredWorkCity')"><NInput v-model:value="draft.personalInfo.preferredWorkCity" /></NFormItem></NGridItem>
          </NGrid>
        </NCollapseItem>

        <NCollapseItem :title="$t('page.resume.education')" name="education">
          <div v-for="(edu, index) in draft.educations" :key="edu._key" class="mb-4 pb-4 border-b border-slate-100 last:border-0">
            <div class="flex justify-between items-center mb-3">
              <span class="font-medium text-slate-700">{{ edu.school || `${$t('page.resume.education')} ${index + 1}` }}</span>
              <NPopconfirm @positive-click="() => removeEducation(index)"><template #trigger><NButton size="tiny" quaternary type="error">{{ $t('page.resume.delete') }}</NButton></template>{{ $t('page.resume.confirmDeleteEdu') }}</NPopconfirm>
            </div>
            <NGrid :cols="2" :x-gap="12">
              <NGridItem><NFormItem :label="$t('page.resume.school')"><NInput v-model:value="edu.school" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.major')"><NInput v-model:value="edu.major" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.degree')"><NSelect v-model:value="edu.degree" :options="degreeOptions" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.gpa')"><NInput v-model:value="edu.gpa" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.startDate')"><NDatePicker :value="eduStart(edu).value" type="date" clearable class="w-full" @update:value="eduStart(edu).value = $event" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.endDate')"><NDatePicker :value="eduEnd(edu).value" type="date" clearable class="w-full" @update:value="eduEnd(edu).value = $event" /></NFormItem></NGridItem>
            </NGrid>
          </div>
          <NButton dashed block @click="addEducation">+ {{ $t('page.resume.addEducation') }}</NButton>
        </NCollapseItem>

        <NCollapseItem :title="$t('page.resume.experience')" name="career">
          <div v-for="(career, index) in draft.careers" :key="career._key" class="mb-4 pb-4 border-b border-slate-100 last:border-0">
            <div class="flex justify-between items-center mb-3">
              <span class="font-medium text-slate-700">{{ career.company || `${$t('page.resume.experience')} ${index + 1}` }}</span>
              <NPopconfirm @positive-click="() => removeCareer(index)"><template #trigger><NButton size="tiny" quaternary type="error">{{ $t('page.resume.delete') }}</NButton></template>{{ $t('page.resume.confirmDeleteCareer') }}</NPopconfirm>
            </div>
            <NGrid :cols="2" :x-gap="12">
              <NGridItem><NFormItem :label="$t('page.resume.company')"><NInput v-model:value="career.company" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.position')"><NInput v-model:value="career.position" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.startDate')"><NDatePicker :value="careerStart(career).value" type="date" clearable class="w-full" @update:value="careerStart(career).value = $event" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.endDate')"><NDatePicker :value="careerEnd(career).value" type="date" clearable class="w-full" @update:value="careerEnd(career).value = $event" /></NFormItem></NGridItem>
            </NGrid>
            <NFormItem :label="$t('page.resume.jobDetails')"><NInput v-model:value="career.details" type="textarea" :autosize="{ minRows: 3 }" /></NFormItem>
          </div>
          <NButton dashed block @click="addCareer">+ {{ $t('page.resume.addCareer') }}</NButton>
        </NCollapseItem>

        <NCollapseItem :title="$t('page.resume.projects')" name="project">
          <div v-for="(project, index) in draft.projects" :key="project._key" class="mb-4 pb-4 border-b border-slate-100 last:border-0">
            <div class="flex justify-between items-center mb-3">
              <span class="font-medium text-slate-700">{{ project.name || `${$t('page.resume.projects')} ${index + 1}` }}</span>
              <NPopconfirm @positive-click="() => removeProject(index)"><template #trigger><NButton size="tiny" quaternary type="error">{{ $t('page.resume.delete') }}</NButton></template>{{ $t('page.resume.confirmDeleteProj') }}</NPopconfirm>
            </div>
            <NGrid :cols="2" :x-gap="12">
              <NGridItem><NFormItem :label="$t('page.resume.projectName')"><NInput v-model:value="project.name" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.role')"><NInput v-model:value="project.role" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.startDate')"><NDatePicker :value="projectStart(project).value" type="date" clearable class="w-full" @update:value="projectStart(project).value = $event" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.endDate')"><NDatePicker :value="projectEnd(project).value" type="date" clearable class="w-full" @update:value="projectEnd(project).value = $event" /></NFormItem></NGridItem>
            </NGrid>
            <NFormItem :label="$t('page.resume.projectDesc')"><NInput v-model:value="project.description" type="textarea" :autosize="{ minRows: 3 }" /></NFormItem>
            <NFormItem :label="$t('page.resume.contributionLabel')"><NInput v-model:value="project.contribution" type="textarea" :autosize="{ minRows: 2 }" /></NFormItem>
            <NFormItem :label="$t('page.profile.project.techStack')"><NDynamicTags v-model:value="project.techStack" /></NFormItem>
          </div>
          <NButton dashed block @click="addProject">+ {{ $t('page.resume.addProject') }}</NButton>
        </NCollapseItem>

        <NCollapseItem :title="$t('page.resume.awards')" name="award">
          <div v-for="(award, index) in draft.awards" :key="award._key" class="mb-4 pb-4 border-b border-slate-100 last:border-0">
            <div class="flex justify-between items-center mb-3">
              <span class="font-medium text-slate-700">{{ award.name || `${$t('page.resume.awards')} ${index + 1}` }}</span>
              <NPopconfirm @positive-click="() => removeAward(index)"><template #trigger><NButton size="tiny" quaternary type="error">{{ $t('page.resume.delete') }}</NButton></template>{{ $t('page.resume.confirmDeleteAward') }}</NPopconfirm>
            </div>
            <NGrid :cols="2" :x-gap="12">
              <NGridItem><NFormItem :label="$t('page.resume.awardType')"><NSelect v-model:value="award.awardType" :options="awardTypeOptions" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.awardName')"><NInput v-model:value="award.name" /></NFormItem></NGridItem>
              <NGridItem><NFormItem :label="$t('page.resume.awardDate')"><NDatePicker :value="awardDateBind(award).value" type="date" clearable class="w-full" @update:value="awardDateBind(award).value = $event" /></NFormItem></NGridItem>
            </NGrid>
            <NFormItem :label="$t('page.resume.awardDescription')"><NInput v-model:value="award.description" type="textarea" :autosize="{ minRows: 2 }" /></NFormItem>
          </div>
          <NButton dashed block @click="addAward">+ {{ $t('page.resume.addAward') }}</NButton>
        </NCollapseItem>

        <NCollapseItem :title="$t('page.resume.skills')" name="skill">
          <div v-for="(group, index) in draft.skill.content" :key="`${group.type}-${index}`" class="mb-4 p-3 rounded-lg bg-slate-50 border border-slate-100">
            <div class="flex justify-between items-center mb-2">
              <NInput v-model:value="group.type" :placeholder="$t('page.resumeImport.skillTypePlaceholder')" />
              <NButton size="tiny" quaternary type="error" @click="removeSkillGroup(index)">{{ $t('page.resume.delete') }}</NButton>
            </div>
            <NDynamicTags v-model:value="group.content" />
          </div>
          <NButton dashed block @click="addSkillGroup">+ {{ $t('page.resumeImport.addSkillGroup') }}</NButton>
        </NCollapseItem>
      </NCollapse>
    </NCard>
  </div>
</template>

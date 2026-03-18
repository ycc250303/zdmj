<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { fetchGetEducationList, fetchDeleteEducation } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';
import EducationForm from './components/EducationForm.vue';

const isEditing = ref(false);
const educationList = ref<ResumeApi.EducationUpdate[]>([]);
const currentEditData = ref<ResumeApi.EducationUpdate | undefined>(undefined);
const loading = ref(true);

function getDegreeText(degreeValue: number) {
  switch (degreeValue) {
    case 1: return $t('page.profile.education.degrees.phd');
    case 2: return $t('page.profile.education.degrees.master');
    case 3: return $t('page.profile.education.degrees.bachelor');
    case 4: return $t('page.profile.education.degrees.associate');
    case 5: return $t('page.profile.education.degrees.highSchool');
    case 6: 
    default: return $t('page.profile.education.degrees.other');
  }
}

async function loadData() {
  loading.value = true;
  try {
    const res = await fetchGetEducationList();
    educationList.value = res.data || []; 
  } finally {
    loading.value = false;
  }
}

function handleAddNew() {
  currentEditData.value = undefined;
  isEditing.value = true;
}

function handleEdit(item: ResumeApi.EducationUpdate) {
  currentEditData.value = { ...item };
  isEditing.value = true;
}

async function handleDelete(id: number) {
  const { error } = await fetchDeleteEducation(id);
  if (!error) {
    window.$message?.success($t('page.profile.common.delete') + '成功');
    loadData(); 
  }
}

function onFormSuccess() {
  isEditing.value = false;
  loadData();
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="p-6 h-full overflow-y-auto bg-gray-50">
    <div v-if="!isEditing" class="max-w-4xl mx-auto">
      <div class="flex justify-between items-center mb-6">
        <h1 class="text-2xl font-bold text-gray-800">{{ $t('page.profile.education.title') }}</h1>
        <NButton type="primary" @click="handleAddNew">
          {{ $t('page.profile.education.addBtn') }}
        </NButton>
      </div>

      <NSpin :show="loading">
        <div v-if="educationList.length === 0" class="text-center py-20 text-gray-400 bg-white rounded-xl border border-gray-100 shadow-sm">
          <div class="i-mdi-school-outline text-6xl mb-4 mx-auto opacity-50"></div>
          <p>{{ $t('page.profile.common.empty') }}</p>
        </div>

        <div v-else class="flex flex-col gap-4">
          <NCard v-for="item in educationList" :key="item.id" hoverable class="rounded-lg shadow-sm border-gray-100">
            <div class="flex justify-between items-start">
              <div>
                <div class="flex items-center gap-3">
                  <h3 class="text-lg font-bold text-gray-800">{{ item.school }}</h3>
                  <NTag v-if="!item.visible" size="small" type="warning" round>{{ $t('page.profile.common.hidden') }}</NTag>
                </div>
                <p class="text-gray-600 font-medium mt-1">
                  {{ getDegreeText(item.degree) }} · {{ item.major }}
                </p>
                <p class="text-gray-400 text-sm mt-1">
                  {{ item.startDate }} ~ {{ item.endDate || $t('page.profile.common.present') }}
                </p>
                <p v-if="item.gpa" class="text-gray-500 mt-2 text-sm bg-gray-100 inline-block px-2 py-1 rounded">
                  {{ $t('page.profile.education.gpa') }}: {{ item.gpa }}
                </p>
              </div>
              <div class="flex gap-2">
                <NButton size="small" secondary @click="handleEdit(item)">{{ $t('page.profile.common.edit') }}</NButton>
                <NPopconfirm @positive-click="handleDelete(item.id)">
                  <template #trigger>
                    <NButton size="small" type="error" ghost>{{ $t('page.profile.common.delete') }}</NButton>
                  </template>
                  {{ $t('page.profile.common.confirmDelete') }}
                </NPopconfirm>
              </div>
            </div>
          </NCard>
        </div>
      </NSpin>
    </div>

    <div v-else class="py-4">
      <EducationForm :initial-data="currentEditData" @success="onFormSuccess" @cancel="isEditing = false" />
    </div>
  </div>
</template>
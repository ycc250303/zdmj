<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { fetchGetCareerList, fetchDeleteCareer } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';
import CareerForm from './components/CareerForm.vue';

const isEditing = ref(false);
const careerList = ref<ResumeApi.CareerUpdate[]>([]);
const currentEditData = ref<ResumeApi.CareerUpdate | undefined>(undefined);
const loading = ref(true);

async function loadData() {
  loading.value = true;
  try {
    const res = await fetchGetCareerList();
    careerList.value = res.data || []; 
  } finally {
    loading.value = false;
  }
}

function handleAddNew() {
  currentEditData.value = undefined;
  isEditing.value = true;
}

function handleEdit(item: ResumeApi.CareerUpdate) {
  currentEditData.value = { ...item };
  isEditing.value = true;
}

async function handleDelete(id: number) {
  const { error } = await fetchDeleteCareer(id);
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
        <h1 class="text-2xl font-bold text-gray-800">{{ $t('page.profile.career.title') }}</h1>
        <NButton type="primary" @click="handleAddNew">
          {{ $t('page.profile.career.addBtn') }}
        </NButton>
      </div>

      <NSpin :show="loading">
        <div v-if="careerList.length === 0" class="text-center py-20 text-gray-400 bg-white rounded-xl border border-gray-100 shadow-sm">
          <div class="i-mdi-briefcase-outline text-6xl mb-4 mx-auto opacity-50"></div>
          <p>{{ $t('page.profile.common.empty') }}</p>
        </div>

        <div v-else class="flex flex-col gap-4">
          <NCard v-for="item in careerList" :key="item.id" hoverable class="rounded-lg shadow-sm border-gray-100">
            <div class="flex justify-between items-start">
              <div class="flex-1">
                <div class="flex items-center gap-3">
                  <h3 class="text-lg font-bold text-gray-800">{{ item.company }}</h3>
                  <NTag v-if="!item.visible" size="small" type="warning" round>{{ $t('page.profile.common.hidden') }}</NTag>
                </div>
                <p class="text-gray-600 font-medium mt-1">{{ item.position }}</p>
                <p class="text-gray-400 text-sm mt-1">
                  {{ item.startDate }} ~ {{ item.endDate || $t('page.profile.common.present') }}
                </p>
                <p class="text-gray-600 mt-3 line-clamp-2" style="white-space: pre-wrap;">{{ item.details }}</p>
              </div>
              <div class="flex gap-2 shrink-0 ml-4">
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
      <CareerForm :initial-data="currentEditData" @success="onFormSuccess" @cancel="isEditing = false" />
    </div>
  </div>
</template>
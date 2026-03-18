<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { fetchGetSkillList, fetchDeleteSkill } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';
import SkillForm from './components/SkillForm.vue';

const isEditing = ref(false);
const skillList = ref<ResumeApi.SkillUpdate[]>([]);
const currentEditData = ref<ResumeApi.SkillUpdate | undefined>(undefined);
const loading = ref(true);


async function loadData() {
  loading.value = true;
  try {
    const res = await fetchGetSkillList();
    const rawList = res.data || []; 
    
    skillList.value = rawList.map((item: any) => {
      let parsedContent = item.content;
      if (typeof parsedContent === 'string') {
        try {
          parsedContent = JSON.parse(parsedContent);
        } catch (e) {
          console.error('技能内容解析失败', e);
          parsedContent = [];
        }
      }
      return {
        ...item,
        content: parsedContent
      };
    });
  } finally {
    loading.value = false;
  }
}

function handleAddNew() {
  currentEditData.value = undefined;
  isEditing.value = true;
}

function handleEdit(item: ResumeApi.SkillUpdate) {
  currentEditData.value = { ...item };
  isEditing.value = true;
}

async function handleDelete(id: number) {
  const { error } = await fetchDeleteSkill(id);
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
        <h1 class="text-2xl font-bold text-gray-800">{{ $t('page.profile.skill.title') }}</h1>
        <NButton type="primary" @click="handleAddNew">
          {{ $t('page.profile.skill.addBtn') }}
        </NButton>
      </div>

      <NSpin :show="loading">
        <div v-if="skillList.length === 0" class="text-center py-20 text-gray-400 bg-white rounded-xl border border-gray-100 shadow-sm">
          <div class="i-mdi-lightning-bolt-outline text-6xl mb-4 mx-auto opacity-50"></div>
          <p>{{ $t('page.profile.common.empty') }}</p>
        </div>

        <div v-else class="flex flex-col gap-4">
          <NCard v-for="item in skillList" :key="item.id" hoverable class="rounded-lg shadow-sm border-gray-100">
            <div class="flex justify-between items-start mb-4">
              <h3 class="text-lg font-bold text-gray-800">{{ item.name }}</h3>
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
            
            <div class="space-y-3 border-t border-gray-50 pt-3">
              <div v-for="(skillGroup, idx) in item.content" :key="idx">
                <div class="text-sm font-semibold text-gray-600 mb-1">{{ skillGroup.type }}</div>
                <div class="flex gap-2 flex-wrap">
                  <NTag v-for="tag in skillGroup.content" :key="tag" type="primary" size="small">{{ tag }}</NTag>
                </div>
              </div>
            </div>
          </NCard>
        </div>
      </NSpin>
    </div>

    <div v-else class="py-4">
      <SkillForm :initial-data="currentEditData" @success="onFormSuccess" @cancel="isEditing = false" />
    </div>
  </div>
</template>
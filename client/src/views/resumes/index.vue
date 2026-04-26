<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';

import { $t } from '@/locales'; 
import { 
  fetchGetResumeList, 
  fetchDeleteResume, 
  fetchAddResume,
  fetchGetSkillList,
  fetchAddSkill
} from '@/service/api/resume';

defineOptions({ name: 'resumes' });

const router = useRouter();

// --- 基础状态 ---
const resumeList = ref<any[]>([]);
const loading = ref(false);

const hasResume = computed(() => resumeList.value.length > 0);
const myResume = computed(() => resumeList.value[0]);

// --- 弹窗与技能交互状态 ---
const showSkillModal = ref(false);
const submitting = ref(false);
const activeTab = ref<'select' | 'create'>('select');
const skillList = ref<any[]>([]);
const selectedSkillId = ref<number | null>(null);

const newSkillForm = reactive({
  name: '',
  content: [{ type: '专业技能', content: ['待补充'] }] 
});

async function loadResumeData() {
  loading.value = true;
  try {
    const { data, error } = await fetchGetResumeList();
    if (!error && data) {
      resumeList.value = data || [];
    }
  } catch (err) {
    window.$message?.error($t('page.resume.getFail'));
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadResumeData();
});

async function handleInitCreateResume() {
  loading.value = true;
  const { data, error } = await fetchGetSkillList();
  loading.value = false;

  if (error) {
    return window.$message?.error(`${$t('page.resume.getFail')}: Skills`);
  }

  skillList.value = data || [];
  
  if (skillList.value.length === 0) {
    activeTab.value = 'create';
  } else {
    activeTab.value = 'select';
    selectedSkillId.value = skillList.value[0].id;
  }
  
  showSkillModal.value = true;
}

async function handleConfirmCreate() {
  submitting.value = true;
  let targetSkillId = selectedSkillId.value;

  try {
    if (activeTab.value === 'create') {
      if (!newSkillForm.name) {
        // 使用国际化提示
        window.$message?.warning(`${$t('page.resume.pleaseInput')}${$t('page.resume.formPlaceholderName')}`);
        submitting.value = false;
        return;
      }
      const { data: skillData, error: skillError } = await fetchAddSkill(newSkillForm);
      if (skillError || !skillData?.id) {
        throw new Error(`${$t('page.resume.createFail')}: Skills`);
      }
      targetSkillId = skillData.id;
    }

    if (!targetSkillId) {
      window.$message?.warning($t('page.resume.noSkillAvailable'));
      submitting.value = false;
      return;
    }

    const { data: resumeData, error: resumeError } = await fetchAddResume({
      name: 'SmartHire Standard Resume',
      skillId: targetSkillId
    });

    if (!resumeError && resumeData?.id) {
      showSkillModal.value = false;
      router.push({ name: 'resumes-editor', query: { id: resumeData.id } });
    } else {
      throw new Error(`${$t('page.resume.createFail')}: Resume`);
    }
  } catch (err: any) {
    window.$message?.error(err.message || 'Operation failed');
  } finally {
    submitting.value = false;
  }
}

function handleEdit() {
  if (myResume.value?.id) {
    router.push({ name: 'resumes-editor', query: { id: myResume.value.id } });
  }
}

function handleDelete() {
  if (!myResume.value?.id) return;
  window.$dialog?.warning({
    title: $t('page.resume.dialogDeleteTitle'),
    content: $t('page.resume.dialogDeleteContent'),
    positiveText: $t('page.resume.dialogDeleteConfirm'),
    negativeText: $t('common.cancel'),
    onPositiveClick: async () => {
      loading.value = true;
      const { error } = await fetchDeleteResume(myResume.value.id);
      loading.value = false;
      if (!error) {
        window.$message?.success($t('page.resume.msgDeleteSuccess'));
        loadResumeData();
      } else {
        window.$message?.error($t('page.resume.msgDeleteFail'));
      }
    }
  });
}
</script>

<template>
  <NSpin :show="loading">
    <div class="h-full p-6 bg-slate-50/50 min-h-[500px]">
    <div class="mb-6">
        <h1 class="text-2xl font-bold text-slate-800">{{ $t('page.resume.myResumes') }}</h1>
        </div>

    <div v-if="hasResume" class="max-w-3xl">
      <NCard hoverable class="rounded-2xl border-slate-200" content-style="padding: 0;">
        <div class="h-40 bg-gradient-to-br from-blue-50 to-indigo-50 p-6 flex flex-col justify-between border-b border-slate-100 relative overflow-hidden group">
          <div class="absolute -right-10 -bottom-10 i-mdi-card-text text-[150px] text-blue-100 opacity-50 transition-transform group-hover:scale-110"></div>
          
          <div class="flex justify-between items-start relative z-10">
            <div class="bg-white/80 text-blue-600 text-sm font-bold px-4 py-1.5 rounded-full shadow-sm backdrop-blur-sm">
              {{ $t('page.resume.tagSmart') }}
            </div>
            <NButton quaternary circle type="error" @click.stop="handleDelete">
              <template #icon><div class="i-mdi-delete-outline"></div></template>
            </NButton>
          </div>
          <h2 class="text-2xl font-bold text-slate-800 relative z-10">{{ myResume.name }}</h2>
        </div>
        
        <div class="p-5 flex items-center justify-between bg-white">
          <div class="text-sm text-slate-500 flex items-center gap-1.5">
            <div class="i-mdi-check-circle-outline text-emerald-500 text-base"></div> {{ $t('page.resume.statusReady') }}
          </div>
          <NButton type="primary" size="large" @click="handleEdit">
            {{ $t('page.resume.editResume') }}
            <template #icon><div class="i-mdi-arrow-right"></div></template>
          </NButton>
        </div>
      </NCard>
    </div>

    <div v-else-if="!loading" class="max-w-3xl">
      <NCard class="rounded-2xl border-dashed border-2 border-slate-300 bg-white flex flex-col items-center justify-center py-16">
        <NEmpty :description="$t('page.resume.emptyDesc')">
          <template #extra>
            <NButton type="primary" size="large" @click="handleInitCreateResume">
              <template #icon><div class="i-mdi-plus-box-outline"></div></template>
              {{ $t('page.resume.createBtn') }}
            </NButton>
          </template>
        </NEmpty>
      </NCard>
    </div>

    <NModal v-model:show="showSkillModal" preset="card" :title="$t('page.resume.modalTitle')" class="w-[500px]">
      <NTabs v-model:value="activeTab" type="line" animated>
        
        <NTabPane name="select" :tab="$t('page.resume.tabSelect')" :disabled="skillList.length === 0">
          <div v-if="skillList.length > 0" class="py-4">
            <NRadioGroup v-model:value="selectedSkillId" name="skillRadios">
              <NSpace vertical>
                <NRadio v-for="skill in skillList" :key="skill.id" :value="skill.id" size="large">
                  {{ skill.name }}
                </NRadio>
              </NSpace>
            </NRadioGroup>
          </div>
          <div v-else class="text-slate-400 py-4 text-center">{{ $t('page.resume.noSkillAvailable') }}</div>
        </NTabPane>

        <NTabPane name="create" :tab="$t('page.resume.tabCreate')">
          <div class="py-4">
            <NFormItem :label="$t('page.resume.formLabelListName')">
              <NInput v-model:value="newSkillForm.name" :placeholder="$t('page.resume.formPlaceholderName')" />
            </NFormItem>
            <NAlert type="info" class="mt-2 text-xs" :show-icon="false">{{ $t('page.resume.alertSkillTip') }}</NAlert>
          </div>
        </NTabPane>
        
      </NTabs>

      <template #footer>
        <div class="flex justify-end gap-3">
          <NButton @click="showSkillModal = false">{{ $t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="submitting" @click="handleConfirmCreate">
            {{ $t('page.resume.modalConfirm') }}
          </NButton>
        </div>
      </template>
    </NModal>
  </div>
  </NSpin>
</template>
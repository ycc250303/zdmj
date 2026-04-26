<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue';
import type { FormInst, FormRules } from 'naive-ui';
import { fetchAddEducation, fetchUpdateEducation } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';

interface Props {
  initialData?: ResumeApi.EducationUpdate;
}
const props = defineProps<Props>();
const emit = defineEmits(['success', 'cancel']);

const formRef = ref<FormInst | null>(null);
const loading = ref(false);

// 使用 computed 保证语言切换时选项同步
const degreeOptions = computed(() => [
  { label: $t('page.profile.education.degrees.phd'), value: 1 },
  { label: $t('page.profile.education.degrees.master'), value: 2 },
  { label: $t('page.profile.education.degrees.bachelor'), value: 3 },
  { label: $t('page.profile.education.degrees.associate'), value: 4 },
  { label: $t('page.profile.education.degrees.highSchool'), value: 5 },
  { label: $t('page.profile.education.degrees.other'), value: 6 }
]);

const formData = reactive<ResumeApi.EducationCreate>({
  school: '',
  major: '',
  degree: 3, 
  startDate: '',
  endDate: '',
  gpa: '',
  visible: true
});

watch(() => props.initialData, (newVal) => {
  if (newVal) Object.assign(formData, newVal);
}, { immediate: true });

const rules = computed<FormRules>(() => ({
  school: [{ required: true, message: $t('page.profile.education.school'), trigger: 'blur' }],
  major: [{ required: true, message: $t('page.profile.education.major'), trigger: 'blur' }],
  degree: [{ required: true, type: 'number', message: $t('page.profile.education.degree'), trigger: 'change' }],
  startDate: [
    { required: true, message: $t('page.profile.education.startDate'), trigger: 'blur' },
    { pattern: /^\d{4}-\d{2}-\d{2}$/, message: $t('page.profile.common.dateFormat'), trigger: 'blur' }
  ],
  endDate: [
    { pattern: /^\d{4}-\d{2}-\d{2}$/, message: $t('page.profile.common.dateFormat'), trigger: 'blur' }
  ]
}));

async function handleSubmit() {
  try {
    await formRef.value?.validate();
    loading.value = true;
    
    const payload = { ...formData };
    if (!payload.endDate) delete payload.endDate;
    if (!payload.gpa) delete payload.gpa;

    if (props.initialData?.id) {
      const { error } = await fetchUpdateEducation({ ...payload, id: props.initialData.id });
      if (!error) {
        window.$message?.success($t('page.profile.education.updateSuccess'));
        emit('success');
      }
    } else {
      const { error } = await fetchAddEducation(payload);
      if (!error) {
        window.$message?.success($t('page.profile.education.addSuccess'));
        emit('success');
      }
    }
  } catch (errors) {
    console.warn('Validate failed', errors);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="max-w-3xl mx-auto bg-white p-8 rounded-xl border border-gray-100 shadow-sm">
    <div class="flex justify-between items-center mb-8">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">{{ props.initialData ? $t('page.profile.common.edit') : $t('page.profile.common.add') }}</h2>
        <p class="text-gray-500 mt-1 text-sm">{{ $t('page.profile.common.requiredDesc') }}</p>
      </div>
      <NButton quaternary circle @click="emit('cancel')">
        <template #icon><div class="i-mdi-close text-xl"></div></template>
      </NButton>
    </div>

    <NForm ref="formRef" :model="formData" :rules="rules" label-placement="top" require-mark-placement="right-hanging">
      <NFormItem :label="$t('page.profile.education.school')" path="school">
        <NInput v-model:value="formData.school" :placeholder="$t('page.profile.education.schoolPlaceholder')" size="large" />
      </NFormItem>

      <NGrid :x-gap="24" :cols="2">
        <NGridItem>
          <NFormItem :label="$t('page.profile.education.major')" path="major">
            <NInput v-model:value="formData.major" :placeholder="$t('page.profile.education.majorPlaceholder')" size="large" />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem :label="$t('page.profile.education.degree')" path="degree">
            <NSelect v-model:value="formData.degree" :options="degreeOptions" size="large" />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <NGrid :x-gap="24" :cols="2">
        <NGridItem>
          <NFormItem :label="$t('page.profile.education.startDate')" path="startDate">
            <NInput v-model:value="formData.startDate" :placeholder="$t('page.profile.common.dateFormat')" size="large" />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem :label="$t('page.profile.education.endDate')" path="endDate">
            <NInput v-model:value="formData.endDate" :placeholder="$t('page.profile.education.endDatePlaceholder')" size="large" />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <NGrid :x-gap="24" :cols="2">
        <NGridItem>
          <NFormItem :label="$t('page.profile.education.gpa')" path="gpa">
            <NInput v-model:value="formData.gpa" :placeholder="$t('page.profile.education.gpaPlaceholder')" size="large" />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem :label="$t('page.profile.common.visibleInResume')" path="visible">
            <NSwitch v-model:value="formData.visible" size="large">
              <template #checked>{{ $t('page.profile.common.visible') }}</template>
              <template #unchecked>{{ $t('page.profile.common.hidden') }}</template>
            </NSwitch>
          </NFormItem>
        </NGridItem>
      </NGrid>

      <div class="flex justify-end gap-4 mt-8 pt-6 border-t border-gray-100">
        <NButton size="large" @click="emit('cancel')">{{ $t('page.profile.common.cancel') }}</NButton>
        <NButton size="large" type="primary" :loading="loading" @click="handleSubmit">
          {{ $t('page.profile.common.save') }}
        </NButton>
      </div>
    </NForm>
  </div>
</template>
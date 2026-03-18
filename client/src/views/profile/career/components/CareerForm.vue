<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue';
import type { FormInst, FormRules } from 'naive-ui';
import { fetchAddCareer, fetchUpdateCareer } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';

interface Props {
  initialData?: ResumeApi.CareerUpdate;
}
const props = defineProps<Props>();
const emit = defineEmits(['success', 'cancel']);

const formRef = ref<FormInst | null>(null);
const loading = ref(false);

const formData = reactive<ResumeApi.CareerCreate>({
  company: '',
  position: '',
  startDate: '',
  endDate: '',
  details: '',
  visible: true
});

watch(() => props.initialData, (newVal) => {
  if (newVal) Object.assign(formData, newVal);
}, { immediate: true });

const rules = computed<FormRules>(() => ({
  company: [{ required: true, message: $t('page.profile.career.company'), trigger: 'blur' }],
  position: [{ required: true, message: $t('page.profile.career.position'), trigger: 'blur' }],
  startDate: [
    { required: true, message: $t('page.profile.career.startDate'), trigger: 'blur' },
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
    if (!payload.details) delete payload.details;

    if (props.initialData?.id) {
      const { error } = await fetchUpdateCareer({ ...payload, id: props.initialData.id });
      if (!error) {
        window.$message?.success($t('page.profile.career.updateSuccess'));
        emit('success');
      }
    } else {
      const { error } = await fetchAddCareer(payload);
      if (!error) {
        window.$message?.success($t('page.profile.career.addSuccess'));
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
      <NGrid :x-gap="24" :cols="2">
        <NGridItem>
          <NFormItem :label="$t('page.profile.career.company')" path="company">
            <NInput v-model:value="formData.company" :placeholder="$t('page.profile.career.companyPlaceholder')" size="large" />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem :label="$t('page.profile.career.position')" path="position">
            <NInput v-model:value="formData.position" :placeholder="$t('page.profile.career.positionPlaceholder')" size="large" />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <NGrid :x-gap="24" :cols="2">
        <NGridItem>
          <NFormItem :label="$t('page.profile.career.startDate')" path="startDate">
            <NInput v-model:value="formData.startDate" :placeholder="$t('page.profile.common.dateFormat')" size="large" />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem :label="$t('page.profile.career.endDate')" path="endDate">
            <NInput v-model:value="formData.endDate" :placeholder="$t('page.profile.career.endDatePlaceholder')" size="large" />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <NFormItem :label="$t('page.profile.career.details')" path="details">
        <NInput v-model:value="formData.details" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" :placeholder="$t('page.profile.career.detailsPlaceholder')" size="large" />
      </NFormItem>

      <NFormItem :label="$t('page.profile.common.visibleInResume')" path="visible">
        <NSwitch v-model:value="formData.visible" size="large">
          <template #checked>{{ $t('page.profile.common.visible') }}</template>
          <template #unchecked>{{ $t('page.profile.common.hidden') }}</template>
        </NSwitch>
      </NFormItem>

      <div class="flex justify-end gap-4 mt-8 pt-6 border-t border-gray-100">
        <NButton size="large" @click="emit('cancel')">{{ $t('page.profile.common.cancel') }}</NButton>
        <NButton size="large" type="primary" :loading="loading" @click="handleSubmit">
          {{ $t('page.profile.common.save') }}
        </NButton>
      </div>
    </NForm>
  </div>
</template>
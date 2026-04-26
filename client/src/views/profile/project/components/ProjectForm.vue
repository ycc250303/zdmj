<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue';
import type { FormInst, FormRules } from 'naive-ui';
import { fetchAddProject, fetchUpdateProject } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';

interface Props {
  initialData?: ResumeApi.ProjectUpdate;
}
const props = defineProps<Props>();
const emit = defineEmits(['success', 'cancel']);

const formRef = ref<FormInst | null>(null);
const loading = ref(false);

const formData = reactive<ResumeApi.ProjectCreate>({
  name: '',
  role: '',
  startDate: '',
  endDate: '',
  description: '',
  contribution: '',
  techStack: [],
  highlights: '',
  url: '',
  visible: true
});

watch(() => props.initialData, (newVal: any) => {
  if (newVal) {
    Object.assign(formData, newVal);
    
    let parsedTech = newVal.techStack;
    if (typeof parsedTech === 'string') {
      try {
        parsedTech = JSON.parse(parsedTech);
      } catch (e) {
        parsedTech = parsedTech.replace(/^\[|\]$/g, '').split(',').map((s:string) => s.trim().replace(/^"|"$/g, '')).filter(Boolean);
      }
    }
    formData.techStack = Array.isArray(parsedTech) ? parsedTech : [];
    if (newVal.highlights) {
      try {
        const parsedHl = JSON.parse(newVal.highlights);
        if (Array.isArray(parsedHl)) {
          formData.highlights = parsedHl.join('\n');
        }
      } catch (e) {
        formData.highlights = newVal.highlights;
      }
    }
  }
}, { immediate: true });

const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: $t('page.profile.project.name'), trigger: 'blur' }],
  role: [{ required: true, message: $t('page.profile.project.role'), trigger: 'blur' }],
  startDate: [
    { required: true, message: $t('page.profile.project.startDate'), trigger: 'blur' },
    { pattern: /^\d{4}-\d{2}-\d{2}$/, message: $t('page.profile.common.dateFormat'), trigger: 'blur' }
  ],endDate: [
    { pattern: /^\d{4}-\d{2}-\d{2}$/, message: $t('page.profile.common.dateFormat'), trigger: 'blur' }
  ],
  description: [{ required: true, message: $t('page.profile.project.description'), trigger: 'blur' }],
  contribution: [{ required: true, message: $t('page.profile.project.contribution'), trigger: 'blur' }]
}));

async function handleSubmit() {
  try {
    await formRef.value?.validate();
    loading.value = true;
    
    const payload = { ...formData };
    payload.techStack = [...(formData.techStack || [])];
    if (!payload.endDate) delete payload.endDate;
    if (!payload.url) delete payload.url;
    if (payload.highlights) {
      payload.highlights = JSON.stringify([payload.highlights]);
    } else {
      delete payload.highlights;
    }

    if (props.initialData?.id) {
      const { error } = await fetchUpdateProject({ ...payload, id: props.initialData.id });
      if (!error) {
        window.$message?.success($t('page.profile.project.updateSuccess'));
        emit('success');
      }
    } else {
      const { error } = await fetchAddProject(payload);
      if (!error) {
        window.$message?.success($t('page.profile.project.addSuccess'));
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
      
      <NFormItem :label="$t('page.profile.project.name')" path="name">
        <NInput v-model:value="formData.name" :placeholder="$t('page.profile.project.namePlaceholder')" size="large" />
      </NFormItem>

      <NFormItem :label="$t('page.profile.project.role')" path="role">
        <NInput v-model:value="formData.role" :placeholder="$t('page.profile.project.rolePlaceholder')" size="large" />
      </NFormItem>

      <NGrid :x-gap="24" :cols="2">
        <NGridItem>
          <NFormItem :label="$t('page.profile.project.startDate')" path="startDate">
            <NInput v-model:value="formData.startDate" :placeholder="$t('page.profile.common.dateFormat')" size="large" />
          </NFormItem>
        </NGridItem>
        <NGridItem>
          <NFormItem :label="$t('page.profile.project.endDate')" path="endDate">
            <NInput v-model:value="formData.endDate" :placeholder="$t('page.profile.project.endDatePlaceholder')" size="large" />
          </NFormItem>
        </NGridItem>
      </NGrid>

      <NFormItem :label="$t('page.profile.project.description')" path="description">
        <NInput v-model:value="formData.description" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" :placeholder="$t('page.profile.project.descPlaceholder')" size="large" />
      </NFormItem>

      <NFormItem :label="$t('page.profile.project.contribution')" path="contribution">
        <NInput v-model:value="formData.contribution" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" :placeholder="$t('page.profile.project.contriPlaceholder')" size="large" />
      </NFormItem>

      <NFormItem :label="$t('page.profile.project.techStack')" path="techStack">
        <NDynamicTags v-model:value="formData.techStack" size="large" />
      </NFormItem>

      <NFormItem :label="$t('page.profile.project.highlights')" path="highlights">
        <NInput v-model:value="formData.highlights" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" :placeholder="$t('page.profile.project.hlPlaceholder')" size="large" />
      </NFormItem>

      <NGrid :x-gap="24" :cols="2">
        <NGridItem>
          <NFormItem :label="$t('page.profile.project.url')" path="url">
            <NInput v-model:value="formData.url" :placeholder="$t('page.profile.project.urlPlaceholder')" size="large" />
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
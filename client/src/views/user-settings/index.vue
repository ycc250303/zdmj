<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormInst, FormRules, SelectOption } from 'naive-ui';
import {
  fetchDeleteUserLlmConfig,
  fetchGetUserLlmConfig,
  fetchListLlmModels,
  fetchSaveUserLlmConfig,
  fetchTestUserLlmConnection,
  type UserLlmConfigApi
} from '@/service/api/user-llm-config';
import { $t } from '@/locales';

const formRef = ref<FormInst | null>(null);
const loading = ref(false);
const saving = ref(false);
const testing = ref(false);
const deleting = ref(false);

const config = ref<UserLlmConfigApi.Config | null>(null);
const modelOptions = ref<SelectOption[]>([]);

const formData = reactive({
  modelCode: '',
  apiKey: ''
});

const rules = computed<FormRules>(() => ({
  modelCode: [{ required: true, message: $t('page.userSettings.modelRequired'), trigger: 'change' }],
  apiKey: [{ required: true, message: $t('page.userSettings.apiKeyRequired'), trigger: 'blur' }]
}));

const statusTag = computed(() => {
  if (!config.value) return null;
  if (config.value.configured) {
    return { type: 'success' as const, label: $t('page.userSettings.statusConfigured') };
  }
  if (config.value.usingPlatformDefault) {
    return { type: 'info' as const, label: $t('page.userSettings.statusPlatformDefault') };
  }
  return { type: 'warning' as const, label: $t('page.userSettings.statusNotConfigured') };
});

async function loadData() {
  loading.value = true;
  try {
    const [configRes, modelsRes] = await Promise.all([fetchGetUserLlmConfig(), fetchListLlmModels()]);
    if (!configRes.error && configRes.data) {
      config.value = configRes.data;
      formData.modelCode = configRes.data.modelCode || '';
    }
    if (!modelsRes.error && modelsRes.data) {
      modelOptions.value = modelsRes.data.map(item => ({
        label: item.displayName,
        value: item.code
      }));
      if (!formData.modelCode && modelOptions.value.length > 0) {
        formData.modelCode = String(modelOptions.value[0].value);
      }
    }
  } finally {
    loading.value = false;
  }
}

async function handleSave() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }

  saving.value = true;
  try {
    const { error } = await fetchSaveUserLlmConfig({
      modelCode: formData.modelCode,
      apiKey: formData.apiKey.trim()
    });
    if (!error) {
      window.$message?.success($t('page.userSettings.saveSuccess'));
      formData.apiKey = '';
      await loadData();
    }
  } finally {
    saving.value = false;
  }
}

async function handleTest() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }

  testing.value = true;
  try {
    const { error } = await fetchTestUserLlmConnection({
      modelCode: formData.modelCode,
      apiKey: formData.apiKey.trim()
    });
    if (!error) {
      window.$message?.success($t('page.userSettings.testSuccess'));
    }
  } finally {
    testing.value = false;
  }
}

function handleDelete() {
  window.$dialog?.warning({
    title: $t('common.tip'),
    content: $t('page.userSettings.deleteConfirm'),
    positiveText: $t('common.confirm'),
    negativeText: $t('common.cancel'),
    onPositiveClick: async () => {
      deleting.value = true;
      try {
        const { error } = await fetchDeleteUserLlmConfig();
        if (!error) {
          window.$message?.success($t('page.userSettings.deleteSuccess'));
          formData.apiKey = '';
          await loadData();
        }
      } finally {
        deleting.value = false;
      }
    }
  });
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="min-h-full p-24px">
    <NSpin :show="loading">
      <div class="mx-auto max-w-720px">
        <div class="mb-24px">
          <h1 class="text-2xl font-bold text-gray-800 dark:text-gray-200">{{ $t('page.userSettings.title') }}</h1>
          <p class="mt-8px text-sm text-gray-500 dark:text-gray-400">{{ $t('page.userSettings.desc') }}</p>
        </div>

        <NCard>
          <div v-if="statusTag" class="mb-20px flex flex-wrap items-center gap-12px">
            <NTag :type="statusTag.type" round>{{ statusTag.label }}</NTag>
            <span v-if="config?.configured && config.modelDisplayName" class="text-sm text-gray-500">
              {{ $t('page.userSettings.currentModel') }}：{{ config.modelDisplayName }}
            </span>
            <span v-if="config?.configured && config.apiKeyMasked" class="text-sm text-gray-500">
              {{ $t('page.userSettings.currentApiKey') }}：{{ config.apiKeyMasked }}
            </span>
          </div>

          <NForm ref="formRef" :model="formData" :rules="rules" label-placement="top" require-mark-placement="right-hanging">
            <NFormItem :label="$t('page.userSettings.model')" path="modelCode">
              <NSelect
                v-model:value="formData.modelCode"
                :options="modelOptions"
                :placeholder="$t('page.userSettings.modelPlaceholder')"
                filterable
              />
            </NFormItem>

            <NFormItem :label="$t('page.userSettings.apiKey')" path="apiKey">
              <NInput
                v-model:value="formData.apiKey"
                type="password"
                show-password-on="click"
                :placeholder="$t('page.userSettings.apiKeyPlaceholder')"
              />
              <template #feedback>
                <span class="text-xs text-gray-400">{{ $t('page.userSettings.apiKeyHint') }}</span>
              </template>
            </NFormItem>

            <div class="flex flex-wrap gap-12px pt-8px">
              <NButton type="primary" :loading="saving" @click="handleSave">
                {{ $t('page.userSettings.save') }}
              </NButton>
              <NButton :loading="testing" @click="handleTest">
                {{ $t('page.userSettings.test') }}
              </NButton>
              <NButton v-if="config?.configured" type="error" ghost :loading="deleting" @click="handleDelete">
                {{ $t('page.userSettings.clearConfig') }}
              </NButton>
            </div>
          </NForm>
        </NCard>
      </div>
    </NSpin>
  </div>
</template>

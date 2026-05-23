<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { $t } from '@/locales';
import {
  fetchGetJobDetail,
  fetchCreateJob,
  fetchUpdateJob,
  type JobApi
} from '@/service/api/job';

defineOptions({ name: 'job-edit' });

const router = useRouter();
const route = useRoute();

const editId = computed(() => (route.query.id ? Number(route.query.id) : null));
const isEdit = computed(() => !!editId.value);

const loading = ref(false);
const submitting = ref(false);

// 表单数据
const formData = reactive<JobApi.JobCreate & { id?: number }>({
  jobName: '',
  companyName: '',
  companySize: undefined,
  companyFundingType: undefined,
  companyIndustries: [],
  companyIntroduction: '',
  description: '',
  location: '',
  salaryMin: 0,
  salaryMax: 0,
  salaryType: 2, // 默认月薪
  link: '',
  jobDuties: [],
  jobRequirements: [],
  keywords: []
});

// 临时存储的数组输入值
const dutiesInput = ref('');
const requirementsInput = ref('');
const keywordsInput = ref('');
const industriesInput = ref('');

// 表单验证
const formRules = computed(() => ({
  jobName: {
    required: true,
    message: $t('page.jobs.formValidation.jobNameRequired'),
    trigger: 'blur'
  },
  companyName: {
    required: true,
    message: $t('page.jobs.formValidation.companyNameRequired'),
    trigger: 'blur'
  },
  description: {
    required: true,
    message: $t('page.jobs.formValidation.descriptionRequired'),
    trigger: 'blur'
  },
  location: {
    required: true,
    message: $t('page.jobs.formValidation.locationRequired'),
    trigger: 'blur'
  },
  salaryMin: {
    required: true,
    type: 'number' as const,
    message: $t('page.jobs.formValidation.salaryMinRequired'),
    trigger: 'blur'
  },
  salaryMax: {
    required: true,
    type: 'number' as const,
    message: $t('page.jobs.formValidation.salaryMaxRequired'),
    trigger: 'blur'
  },
  salaryType: {
    required: true,
    type: 'number' as const,
    message: $t('page.jobs.formValidation.salaryTypeRequired'),
    trigger: 'change'
  },
  link: {
    required: true,
    message: $t('page.jobs.formValidation.linkRequired'),
    trigger: 'blur'
  }
}));

const formRef = ref<any>();

async function loadJobDetail() {
  if (!editId.value) return;

  loading.value = true;
  try {
    const { data, error } = await fetchGetJobDetail(editId.value);
    if (!error && data) {
      Object.assign(formData, {
        id: data.id,
        jobName: data.jobName,
        companyName: data.companyName,
        description: data.description,
        location: data.location,
        salaryMin: data.salaryMin,
        salaryMax: data.salaryMax,
        salaryType: data.salaryType,
        link: data.link
      });

      // 设置数组字段
      formData.jobDuties = data.jobDuties || [];
      formData.jobRequirements = data.jobRequirements || [];
      formData.keywords = data.keywords || [];
      formData.companyIndustries = data.companyIndustries || [];

      // 设置临时输入值
      dutiesInput.value = (formData.jobDuties || []).join('\n');
      requirementsInput.value = (formData.jobRequirements || []).join('\n');
      keywordsInput.value = (formData.keywords || []).join('\n');
      industriesInput.value = (formData.companyIndustries || []).join('\n');
    } else {
      window.$message?.error($t('page.jobs.loadFailed'));
      router.back();
    }
  } catch (err) {
    window.$message?.error($t('page.jobs.loadFailed'));
    router.back();
  } finally {
    loading.value = false;
  }
}

async function handleSubmit() {
  try {
    await formRef.value?.validate();

    // 解析数组字段
    formData.jobDuties = dutiesInput.value
      .split('\n')
      .map(s => s.trim())
      .filter(s => s.length > 0);

    formData.jobRequirements = requirementsInput.value
      .split('\n')
      .map(s => s.trim())
      .filter(s => s.length > 0);

    formData.keywords = keywordsInput.value
      .split(/[,，\n]/)
      .map(s => s.trim())
      .filter(s => s.length > 0);

    formData.companyIndustries = industriesInput.value
      .split('\n')
      .map(s => s.trim())
      .filter(s => s.length > 0);

    // 验证薪资范围
    if (formData.salaryMin > formData.salaryMax) {
      window.$message?.error($t('page.jobs.formValidation.salaryRangeInvalid'));
      return;
    }

    submitting.value = true;

    const { data, error } = isEdit.value
      ? await fetchUpdateJob(formData as JobApi.JobUpdate)
      : await fetchCreateJob(formData);

    if (!error && data) {
      window.$message?.success(isEdit.value ? $t('page.jobs.updateSuccess') : $t('page.jobs.createSuccess'));
      router.push({ name: 'jobs' });
    } else {
      window.$message?.error(isEdit.value ? $t('page.jobs.updateFailed') : $t('page.jobs.createFailed'));
    }
  } catch (err) {
    console.error('表单验证失败:', err);
  } finally {
    submitting.value = false;
  }
}

function handleCancel() {
  router.back();
}

onMounted(() => {
  if (isEdit.value) {
    loadJobDetail();
  }
});
</script>

<template>
  <NSpin :show="loading">
    <div class="h-full p-6 bg-slate-50/50 min-h-[500px] overflow-auto">
      <!-- 头部 -->
      <div class="mb-6 flex items-center gap-3">
        <NButton quaternary circle @click="handleCancel">
          <template #icon><span>←</span></template>
        </NButton>
        <h1 class="text-2xl font-bold text-slate-800">{{ isEdit ? $t('page.jobs.edit') : $t('page.jobs.create') }}</h1>
      </div>

      <!-- 表单 -->
      <div class="max-w-4xl">
        <NForm ref="formRef" :model="formData" :rules="formRules" label-placement="left" label-width="120px">
          <NCard :title="$t('page.jobs.basicInfo')" class="mb-4 rounded-xl">
            <NFormItem :label="$t('page.jobs.jobName')" path="jobName">
              <NInput v-model:value="formData.jobName" :placeholder="$t('page.jobs.placeholders.jobName')" />
            </NFormItem>

            <NFormItem :label="$t('page.jobs.companyName')" path="companyName">
              <NInput v-model:value="formData.companyName" :placeholder="$t('page.jobs.placeholders.companyName')" />
            </NFormItem>

            <NFormItem :label="$t('page.jobs.companyIndustry')">
              <NInput
                v-model:value="industriesInput"
                type="textarea"
                :rows="3"
                :placeholder="$t('page.jobs.placeholders.industries')"
              />
              <template #feedback>
                <div class="text-xs text-slate-500">
                  {{ $t('page.jobs.tips.industriesFormat') }}
                </div>
              </template>
            </NFormItem>

            <NFormItem :label="$t('page.jobs.location')" path="location">
              <NInput v-model:value="formData.location" :placeholder="$t('page.jobs.placeholders.location')" />
            </NFormItem>
          </NCard>

          <NCard :title="$t('page.jobs.salaryInfo')" class="mb-4 rounded-xl">
            <NFormItem :label="$t('page.jobs.salaryMin')" path="salaryMin">
              <NInputNumber v-model:value="formData.salaryMin" :min="0" :placeholder="$t('page.jobs.salaryMin')" class="w-full" />
            </NFormItem>

            <NFormItem :label="$t('page.jobs.salaryMax')" path="salaryMax">
              <NInputNumber v-model:value="formData.salaryMax" :min="0" :placeholder="$t('page.jobs.salaryMax')" class="w-full" />
            </NFormItem>

            <NFormItem :label="$t('page.jobs.salaryType')" path="salaryType">
              <NRadioGroup v-model:value="formData.salaryType">
                <NRadio :value="1">{{ $t('page.jobs.daily') }}</NRadio>
                <NRadio :value="2">{{ $t('page.jobs.monthly') }}</NRadio>
                <NRadio :value="3">{{ $t('page.jobs.yearly') }}</NRadio>
              </NRadioGroup>
            </NFormItem>
          </NCard>

          <NCard :title="$t('page.jobs.jobDescription')" class="mb-4 rounded-xl">
            <NFormItem :label="$t('page.jobs.jobDescription')" path="description">
              <NInput
                v-model:value="formData.description"
                type="textarea"
                :rows="6"
                :placeholder="$t('page.jobs.placeholders.description')"
              />
            </NFormItem>

            <NFormItem :label="$t('page.jobs.jobDuties')">
              <NInput
                v-model:value="dutiesInput"
                type="textarea"
                :rows="5"
                :placeholder="$t('page.jobs.tips.dutiesFormat')"
              />
              <template #feedback>
                <div class="text-xs text-slate-500">
                  {{ $t('page.jobs.tips.dutiesFormat') }}
                </div>
              </template>
            </NFormItem>

            <NFormItem :label="$t('page.jobs.jobRequirements')">
              <NInput
                v-model:value="requirementsInput"
                type="textarea"
                :rows="5"
                :placeholder="$t('page.jobs.tips.requirementsFormat')"
              />
              <template #feedback>
                <div class="text-xs text-slate-500">
                  {{ $t('page.jobs.tips.requirementsFormat') }}
                </div>
              </template>
            </NFormItem>

            <NFormItem :label="$t('page.jobs.jobKeywords')">
              <NInput
                v-model:value="keywordsInput"
                type="textarea"
                :rows="3"
                :placeholder="$t('page.jobs.tips.keywordsFormat')"
              />
              <template #feedback>
                <div class="text-xs text-slate-500">
                  {{ $t('page.jobs.tips.keywordsFormat') }}
                </div>
              </template>
            </NFormItem>
          </NCard>

          <NCard :title="$t('page.jobs.otherInfo')" class="mb-4 rounded-xl">
            <NFormItem :label="$t('page.jobs.jobLink')" path="link">
              <NInput v-model:value="formData.link" :placeholder="$t('page.jobs.placeholders.link')" />
            </NFormItem>

            <NFormItem :label="$t('page.jobs.companyIntro')">
              <NInput
                v-model:value="formData.companyIntroduction"
                type="textarea"
                :rows="4"
                :placeholder="$t('page.jobs.placeholders.companyIntro')"
              />
            </NFormItem>
          </NCard>
        </NForm>

        <!-- 操作按钮 -->
        <div class="flex justify-end gap-3 mt-6">
          <NButton size="large" @click="handleCancel">{{ $t('common.cancel') }}</NButton>
          <NButton type="primary" size="large" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? $t('common.modify') : $t('page.jobs.create') }}
          </NButton>
        </div>
      </div>
    </div>
  </NSpin>
</template>

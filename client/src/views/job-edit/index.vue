<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
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

const salaryTypeOptions = [
  { label: '日薪', value: 1 },
  { label: '月薪', value: 2 },
  { label: '年薪', value: 3 }
];

const employmentOptions = [
  { label: '实习', value: 'INTERN' },
  { label: '全职', value: 'FULLTIME' }
];

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
const formRules = {
  jobName: {
    required: true,
    message: '请输入岗位名称',
    trigger: 'blur'
  },
  companyName: {
    required: true,
    message: '请输入公司名称',
    trigger: 'blur'
  },
  description: {
    required: true,
    message: '请输入岗位描述',
    trigger: 'blur'
  },
  location: {
    required: true,
    message: '请输入工作地点',
    trigger: 'blur'
  },
  salaryMin: {
    required: true,
    type: 'number',
    message: '请输入最低薪资',
    trigger: 'blur'
  },
  salaryMax: {
    required: true,
    type: 'number',
    message: '请输入最高薪资',
    trigger: 'blur'
  },
  salaryType: {
    required: true,
    type: 'number',
    message: '请选择薪资类型',
    trigger: 'change'
  },
  link: {
    required: true,
    message: '请输入岗位链接',
    trigger: 'blur'
  }
};

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
      formData.jobDuties = data.content || [];
      formData.jobRequirements = data.requirements || [];
      formData.keywords = data.keywords || [];
      formData.companyIndustries = data.companyIndustries || [];

      // 设置临时输入值
      dutiesInput.value = formData.jobDuties.join('\n');
      requirementsInput.value = formData.jobRequirements.join('\n');
      keywordsInput.value = formData.keywords.join('\n');
      industriesInput.value = formData.companyIndustries.join('\n');
    } else {
      window.$message?.error('加载岗位详情失败');
      router.back();
    }
  } catch (err) {
    window.$message?.error('加载岗位详情失败');
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
      window.$message?.error('最低薪资不能大于最高薪资');
      return;
    }

    submitting.value = true;

    const { data, error } = isEdit.value
      ? await fetchUpdateJob(formData as JobApi.JobUpdate)
      : await fetchCreateJob(formData);

    if (!error && data) {
      window.$message?.success(isEdit.value ? '更新成功' : '创建成功');
      router.push({ name: 'job-detail', params: { id: data.id } });
    } else {
      window.$message?.error(isEdit.value ? '更新失败' : '创建失败');
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
          <template #icon><div class="i-mdi-arrow-left"></div></template>
        </NButton>
        <h1 class="text-2xl font-bold text-slate-800">{{ isEdit ? '编辑岗位' : '创建岗位' }}</h1>
      </div>

      <!-- 表单 -->
      <div class="max-w-4xl">
        <NForm ref="formRef" :model="formData" :rules="formRules" label-placement="left" label-width="120px">
          <NCard title="基本信息" class="mb-4 rounded-xl">
            <NFormItem label="岗位名称" path="jobName">
              <NInput v-model:value="formData.jobName" placeholder="请输入岗位名称" />
            </NFormItem>

            <NFormItem label="公司名称" path="companyName">
              <NInput v-model:value="formData.companyName" placeholder="请输入公司名称" />
            </NFormItem>

            <NFormItem label="公司行业">
              <NInput
                v-model:value="industriesInput"
                type="textarea"
                :rows="3"
                placeholder="每行输入一个行业，例如：&#10;互联网&#10;企业服务&#10;金融科技"
              />
              <template #feedback>
                <div class="text-xs text-slate-500">
                  每行输入一个行业标签
                </div>
              </template>
            </NFormItem>

            <NFormItem label="工作地点" path="location">
              <NInput v-model:value="formData.location" placeholder="请输入工作地点，例如：北京、上海、远程" />
            </NFormItem>
          </NCard>

          <NCard title="薪资信息" class="mb-4 rounded-xl">
            <NFormItem label="最低薪资" path="salaryMin">
              <NInputNumber v-model:value="formData.salaryMin" :min="0" placeholder="请输入最低薪资" class="w-full" />
            </NFormItem>

            <NFormItem label="最高薪资" path="salaryMax">
              <NInputNumber v-model:value="formData.salaryMax" :min="0" placeholder="请输入最高薪资" class="w-full" />
            </NFormItem>

            <NFormItem label="薪资类型" path="salaryType">
              <NRadioGroup v-model:value="formData.salaryType">
                <NRadio v-for="option in salaryTypeOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </NRadio>
              </NRadioGroup>
            </NFormItem>
          </NCard>

          <NCard title="岗位描述" class="mb-4 rounded-xl">
            <NFormItem label="岗位描述" path="description">
              <NInput
                v-model:value="formData.description"
                type="textarea"
                :rows="6"
                placeholder="请输入岗位描述，包括岗位背景、工作内容等"
              />
            </NFormItem>

            <NFormItem label="岗位职责">
              <NInput
                v-model:value="dutiesInput"
                type="textarea"
                :rows="5"
                placeholder="每行输入一条职责，例如：&#10;负责后端系统设计与开发&#10;参与技术方案评审&#10;编写技术文档"
              />
              <template #feedback>
                <div class="text-xs text-slate-500">
                  每行输入一条岗位职责
                </div>
              </template>
            </NFormItem>

            <NFormItem label="岗位要求">
              <NInput
                v-model:value="requirementsInput"
                type="textarea"
                :rows="5"
                placeholder="每行输入一条要求，例如：&#10;本科及以上学历，计算机相关专业&#10;3年以上后端开发经验&#10;熟练掌握Java/Python等语言"
              />
              <template #feedback>
                <div class="text-xs text-slate-500">
                  每行输入一条岗位要求
                </div>
              </template>
            </NFormItem>

            <NFormItem label="岗位关键词">
              <NInput
                v-model:value="keywordsInput"
                type="textarea"
                :rows="3"
                placeholder="可以使用逗号、空格或换行分隔，例如：&#10;Java, Spring Boot, MySQL&#10;或者：Java&#10;Spring Boot&#10;MySQL"
              />
              <template #feedback>
                <div class="text-xs text-slate-500">
                  支持使用逗号、空格或换行分隔关键词
                </div>
              </template>
            </NFormItem>
          </NCard>

          <NCard title="其他信息" class="mb-4 rounded-xl">
            <NFormItem label="岗位链接" path="link">
              <NInput v-model:value="formData.link" placeholder="请输入岗位链接，例如：BOSS直聘、拉勾等" />
            </NFormItem>

            <NFormItem label="公司介绍">
              <NInput
                v-model:value="formData.companyIntroduction"
                type="textarea"
                :rows="4"
                placeholder="请输入公司介绍（可选）"
              />
            </NFormItem>
          </NCard>
        </NForm>

        <!-- 操作按钮 -->
        <div class="flex justify-end gap-3 mt-6">
          <NButton size="large" @click="handleCancel">取消</NButton>
          <NButton type="primary" size="large" :loading="submitting" @click="handleSubmit">
            {{ isEdit ? '保存修改' : '创建岗位' }}
          </NButton>
        </div>
      </div>
    </div>
  </NSpin>
</template>

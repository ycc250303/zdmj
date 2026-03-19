<script setup lang="ts">
import { ref, reactive, watch, computed, onMounted } from 'vue';
import type { FormInst, FormRules, UploadFileInfo } from 'naive-ui';
import { fetchCreateKnowledge, fetchUpdateKnowledge, fetchUploadFile } from '@/service/api/knowledge';
import type { KnowledgeApi } from '@/service/api/knowledge';
import { fetchGetProjectList } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';

interface Props {
  initialData?: KnowledgeApi.KnowledgeUpdate;
}
const props = defineProps<Props>();
const emit = defineEmits(['success', 'cancel']);

const formRef = ref<FormInst | null>(null);
const loading = ref(false);
const uploadLoading = ref(false);
const projectList = ref<ResumeApi.ProjectDTO[]>([]);
const projectLoading = ref(false);
const fileList = ref<UploadFileInfo[]>([]);

const formData = reactive<KnowledgeApi.KnowledgeCreate>({
  name: '',
  projectId: undefined as unknown as number,
  type: 2,
  content: '',
  tag: []
});

watch(
  () => props.initialData,
  newVal => {
    if (newVal) {
      formData.name = newVal.name;
      formData.projectId = newVal.projectId;
      formData.type = newVal.type;
      formData.content = newVal.content;
      formData.tag = newVal.tag ? [...newVal.tag] : [];
    }
  },
  { immediate: true }
);

const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }],
  projectId: [{ required: true, type: 'number', message: '请选择关联项目', trigger: 'change' }],
  type: [{ required: true, type: 'number', message: '请选择知识类型', trigger: 'change' }],
  content: [
    { required: true, message: '请输入内容链接', trigger: 'blur' },
    {
      validator: (_rule, value) => {
        if (value && !value.startsWith('http://') && !value.startsWith('https://')) {
          return new Error('内容必须是有效的URL链接');
        }
        return true;
      },
      trigger: 'blur'
    }
  ]
}));

const knowledgeTypeOptions = [
  { label: '项目文档（PDF/MD）', value: 1 },
  { label: 'GitHub 仓库代码', value: 2 }
  // type=3 DeepWiki 暂不支持
];

const contentPlaceholder = computed(() => {
  if (formData.type === 1) {
    return '请先上传文件，或输入 COS 文件链接（支持 .pdf 或 .md）';
  }
  return '请输入 GitHub 仓库链接，如 https://github.com/username/repo';
});

async function loadProjects() {
  projectLoading.value = true;
  try {
    const { data, error } = await fetchGetProjectList();
    if (!error && data) {
      projectList.value = data;
    }
  } finally {
    projectLoading.value = false;
  }
}

async function handleUpload(options: { file: UploadFileInfo }) {
  const file = options.file.file;
  if (!file) return;

  // 检查文件类型
  const fileName = file.name.toLowerCase();
  if (!fileName.endsWith('.pdf') && !fileName.endsWith('.md') && !fileName.endsWith('.markdown')) {
    window.$message?.error('仅支持 PDF 和 Markdown 文件');
    return;
  }

  uploadLoading.value = true;
  try {
    const { data, error } = await fetchUploadFile(file);

    if (!error && data) {
      formData.content = data.url;
      // 更新文件列表，让用户看到上传的文件
      fileList.value = [
        {
          id: Date.now().toString(),
          name: file.name,
          status: 'finished',
          url: data.url
        }
      ];
      window.$message?.success('文件上传成功');
    } else if (error) {
      window.$message?.error(`文件上传失败: ${error.message || '未知错误'}`);
    }
  } catch (err) {
    window.$message?.error(`文件上传失败: ${(err as Error).message}`);
  } finally {
    uploadLoading.value = false;
  }
}

async function handleFileChange(options: { fileList: UploadFileInfo[] }) {
  const file = options.fileList[0];
  if (!file || file.status !== 'pending') return;

  const fileObj = file.file;
  if (!fileObj) return;

  // 检查文件类型
  const fileName = fileObj.name.toLowerCase();
  if (!fileName.endsWith('.pdf') && !fileName.endsWith('.md') && !fileName.endsWith('.markdown')) {
    window.$message?.error('仅支持 PDF 和 Markdown 文件');
    fileList.value = [];
    return;
  }

  uploadLoading.value = true;
  try {
    const { data, error } = await fetchUploadFile(fileObj);

    if (!error && data) {
      formData.content = data.url;
      // 更新文件列表，标记为完成
      fileList.value = [
        {
          id: file.id,
          name: fileObj.name,
          status: 'finished',
          url: data.url
        }
      ];
      window.$message?.success('文件上传成功');
    } else if (error) {
      fileList.value = [];
      window.$message?.error(`文件上传失败: ${error.message || '未知错误'}`);
    }
  } catch (err) {
    fileList.value = [];
    window.$message?.error(`文件上传失败: ${(err as Error).message}`);
  } finally {
    uploadLoading.value = false;
  }
}

function handleFileRemove() {
  formData.content = '';
  fileList.value = [];
}

async function handleSubmit() {
  try {
    await formRef.value?.validate();

    // type=1 时额外验证文件类型
    if (formData.type === 1) {
      const lowerContent = formData.content.toLowerCase();
      const isPdf = lowerContent.includes('.pdf') || lowerContent.includes('/pdf/');
      const isMd = lowerContent.includes('.md') || lowerContent.endsWith('.md');
      if (!isPdf && !isMd) {
        window.$message?.error('项目文档类型仅支持 PDF 和 Markdown 文件');
        return;
      }
    }

    // type=2 时验证 GitHub 链接
    if (formData.type === 2) {
      if (!formData.content.includes('github.com')) {
        window.$message?.error('GitHub 链接类型必须是 GitHub 仓库链接');
        return;
      }
    }

    loading.value = true;

    if (props.initialData?.id) {
      const { data, error } = await fetchUpdateKnowledge({ ...formData, id: props.initialData.id });
      if (!error && data) {
        window.$message?.success('知识库更新成功');
        emit('success', data);
      }
    } else {
      const { data, error } = await fetchCreateKnowledge(formData);
      if (!error && data) {
        window.$message?.success('知识库创建成功，向量化任务已启动');
        emit('success', data);
      }
    }
  } catch (errors) {
    console.warn('Validate failed', errors);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadProjects();
});
</script>

<template>
  <div class="max-w-3xl mx-auto bg-white p-8 rounded-xl border border-gray-100 shadow-sm">
    <div class="flex justify-between items-center mb-8">
      <div>
        <h2 class="text-2xl font-bold text-gray-800">
          {{ props.initialData ? $t('page.profile.common.edit') : $t('page.profile.common.add') }}
        </h2>
        <p class="text-gray-500 mt-1 text-sm">{{ $t('page.profile.common.requiredDesc') }}</p>
      </div>
      <NButton quaternary circle @click="emit('cancel')">
        <template #icon>
          <div class="i-mdi-close text-xl"></div>
        </template>
      </NButton>
    </div>

    <NForm
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-placement="top"
      require-mark-placement="right-hanging"
    >
      <NFormItem label="知识库名称" path="name">
        <NInput v-model:value="formData.name" placeholder="请输入知识库名称" size="large" />
      </NFormItem>

      <NFormItem label="关联项目" path="projectId">
        <NSelect
          v-model:value="formData.projectId"
          :options="projectList.map(p => ({ label: p.name, value: p.id }))"
          placeholder="请选择关联项目"
          :loading="projectLoading"
        />
      </NFormItem>

      <NFormItem label="知识类型" path="type">
        <NRadioGroup v-model:value="formData.type">
          <NSpace>
            <NRadio v-for="option in knowledgeTypeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </NRadio>
          </NSpace>
        </NRadioGroup>
      </NFormItem>

      <!-- type=1 项目文档：显示上传组件 -->
      <NFormItem v-if="formData.type === 1" label="文档文件" path="content">
        <div class="w-full space-y-4">
          <NUpload
            :file-list="fileList"
            :max="1"
            accept=".pdf,.md,.markdown"
            @change="handleFileChange"
            @remove="handleFileRemove"
          >
            <NButton :loading="uploadLoading">
              <template #icon>
                <div class="i-mdi-upload"></div>
              </template>
              上传 PDF 或 Markdown 文件
            </NButton>
          </NUpload>
          <NDivider class="!my-2">或直接输入链接</NDivider>
          <NInput
            v-model:value="formData.content"
            placeholder="输入 COS 文件链接（支持 .pdf 或 .md）"
          >
            <template #prefix>
              <div class="i-mdi-link text-gray-400"></div>
            </template>
          </NInput>
        </div>
      </NFormItem>

      <!-- type=2 GitHub 代码：只显示 URL 输入框 -->
      <NFormItem v-else-if="formData.type === 2" label="GitHub 仓库链接" path="content">
        <NInput v-model:value="formData.content" :placeholder="contentPlaceholder">
          <template #prefix>
            <div class="i-mdi-github text-gray-400"></div>
          </template>
        </NInput>
        <template #feedback>
          <p class="text-gray-400 text-xs mt-1">示例：https://github.com/username/repository</p>
        </template>
      </NFormItem>

      <NFormItem label="知识标签">
        <NDynamicTags v-model:value="formData.tag" />
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

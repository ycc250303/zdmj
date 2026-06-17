<script setup lang="ts">
import { ref, reactive, watch, computed, onMounted } from 'vue';
import type { FormInst, FormRules, UploadFileInfo } from 'naive-ui';
import {
  fetchCreateKnowledgeDocument,
  fetchUpdateKnowledgeDocument,
  fetchUploadFile
} from '@/service/api/knowledge';
import type { KnowledgeApi } from '@/service/api/knowledge';
import { $t } from '@/locales';

interface Props {
  initialData?: KnowledgeApi.KnowledgeDocumentUpdate;
}
const props = defineProps<Props>();
const emit = defineEmits(['success', 'cancel']);

const formRef = ref<FormInst | null>(null);
const loading = ref(false);
const uploadLoading = ref(false);
const fileList = ref<UploadFileInfo[]>([]);
const isDragging = ref(false);

const formData = reactive<KnowledgeApi.KnowledgeDocumentCreate>({
  title: '',
  type: 1,
  content: ''
});

watch(
  () => props.initialData,
  newVal => {
    if (newVal) {
      formData.title = newVal.title;
      formData.type = newVal.type;
      formData.content = newVal.content;
    }
  },
  { immediate: true }
);

const rules = computed<FormRules>(() => ({
  title: [{ required: true, message: '请输入文档标题', trigger: 'blur' }],
  type: [{ required: true, type: 'number', message: '请选择知识类型', trigger: 'change' }],
  content: [
    { required: true, message: '请上传 PDF 或 Markdown 文件', trigger: ['blur', 'change'] },
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
  { label: '项目文档（PDF/MD）', value: 1 }
  // type=2 GitHub 暂不支持
  // type=3 DeepWiki 暂不支持
];

const showTypeSelector = computed(() => knowledgeTypeOptions.length > 1);

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

// 拖拽事件处理
function handleDragOver(e: DragEvent) {
  e.preventDefault();
  isDragging.value = true;
}

function handleDragLeave(e: DragEvent) {
  e.preventDefault();
  isDragging.value = false;
}

function handleDrop(e: DragEvent) {
  e.preventDefault();
  isDragging.value = false;

  const files = e.dataTransfer?.files;
  if (!files || files.length === 0) return;

  const file = files[0];
  const fileName = file.name.toLowerCase();

  // 检查文件类型
  if (!fileName.endsWith('.pdf') && !fileName.endsWith('.md') && !fileName.endsWith('.markdown')) {
    window.$message?.error('仅支持 PDF 和 Markdown 文件');
    return;
  }

  // 创建 UploadFileInfo 对象
  const uploadFile: UploadFileInfo = {
    id: Date.now().toString(),
    name: file.name,
    status: 'pending',
    file: file
  };

  fileList.value = [uploadFile];

  // 触发上传
  handleFileChange({ fileList: [uploadFile] });
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

    loading.value = true;

    if (props.initialData?.id) {
      const { data, error } = await fetchUpdateKnowledgeDocument({
        ...formData,
        id: props.initialData.id
      });
      if (!error && data) {
        window.$message?.success('知识文档更新成功');
        emit('success', data);
      }
    } else {
      const { data, error } = await fetchCreateKnowledgeDocument(formData);
      if (!error && data) {
        window.$message?.success('知识文档创建成功，向量化任务已启动');
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
  // 不再需要加载项目列表
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
          <icon-carbon-close class="text-18px" />
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
      <NFormItem :label="$t('page.knowledge.docTitle')" path="title">
        <NInput v-model:value="formData.title" :placeholder="$t('page.knowledge.docTitlePlaceholder')" size="large" />
      </NFormItem>

      <NFormItem v-if="showTypeSelector" :label="$t('page.knowledge.type')" path="type">
        <NRadioGroup v-model:value="formData.type">
          <NSpace>
            <NRadio v-for="option in knowledgeTypeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </NRadio>
          </NSpace>
        </NRadioGroup>
      </NFormItem>

      <!-- 项目文档：显示上传组件 -->
      <NFormItem :label="$t('page.knowledge.uploadFile')" path="content">
        <div class="w-full space-y-4">
          <NUpload
            :file-list="fileList"
            :max="1"
            accept=".pdf,.md,.markdown"
            @change="handleFileChange"
            @remove="handleFileRemove"
          >
            <div
              class="upload-area border-2 border-dashed rounded-lg p-8 text-center transition-all cursor-pointer"
              :class="isDragging ? 'border-blue-500 bg-blue-50' : 'border-gray-300 hover:border-blue-400'"
              @dragover="handleDragOver"
              @dragleave="handleDragLeave"
              @drop="handleDrop"
            >
              <span class="block mx-auto mb-2" :class="isDragging ? 'text-blue-500' : 'text-gray-400'">
                <icon-carbon-cloud-upload class="text-32px mx-auto" />
              </span>
              <p class="mb-1" :class="isDragging ? 'text-blue-600 font-medium' : 'text-gray-600'">
                {{ isDragging ? $t('page.knowledge.dragUploadActive') : $t('page.knowledge.dragUpload') }}
              </p>
              <p class="text-xs text-gray-400">{{ $t('page.knowledge.uploadTip') }}</p>
            </div>
          </NUpload>
        </div>
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

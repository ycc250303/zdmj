<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import type { FormInst, FormRules, UploadFileInfo } from 'naive-ui';
import { fetchCreateKnowledgeDocument, fetchUpdateKnowledgeDocument } from '@/service/api/knowledge';
import type { KnowledgeApi } from '@/service/api/knowledge';
import { $t } from '@/locales';

interface Props {
  initialData?: KnowledgeApi.KnowledgeDocumentUpdate;
}
const props = defineProps<Props>();
const emit = defineEmits(['success', 'cancel', 'refresh']);

const formRef = ref<FormInst | null>(null);
const loading = ref(false);
const selectedFile = ref<File | null>(null);
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
    selectedFile.value = null;
    fileList.value = [];
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
    {
      validator: () => (selectedFile.value || props.initialData?.id ? true : new Error('请选择 PDF 或 Markdown 文件')),
      trigger: 'change'
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

  selectedFile.value = null;
  const fileObj = file.file;
  if (!fileObj) return;

  // 检查文件类型
  const fileName = fileObj.name.toLowerCase();
  if (!fileName.endsWith('.pdf') && !fileName.endsWith('.md') && !fileName.endsWith('.markdown')) {
    window.$message?.error('仅支持 PDF 和 Markdown 文件');
    fileList.value = [];
    return;
  }

  if (fileObj.size > 20 * 1024 * 1024) {
    window.$message?.error('文件大小不能超过 20MB');
    fileList.value = [];
    return;
  }
  selectedFile.value = fileObj;
  fileList.value = [file];
}

function handleFileRemove() {
  selectedFile.value = null;
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
    file
  };

  fileList.value = [uploadFile];

  // 触发上传
  handleFileChange({ fileList: [uploadFile] });
}

async function handleSubmit() {
  try {
    await formRef.value?.validate();

    loading.value = true;

    if (props.initialData?.id) {
      const { data, error } = await fetchUpdateKnowledgeDocument(
        {
          ...formData,
          id: props.initialData.id
        },
        selectedFile.value || undefined
      );
      if (!error && data) {
        window.$message?.success('知识文档更新成功');
        emit('success', data);
      }
    } else {
      if (!selectedFile.value) return;
      const { data, error } = await fetchCreateKnowledgeDocument(formData, selectedFile.value);
      if (!error && data) {
        window.$message?.success('知识文档保存并向量化成功');
        emit('success', data);
      } else if ((error?.response?.data as { documentId?: number } | undefined)?.documentId) {
        emit('cancel');
      }
    }
  } catch (errors) {
    console.warn('Validate failed', errors);
  } finally {
    emit('refresh');
    loading.value = false;
  }
}
</script>

<template>
  <div class="mx-auto max-w-3xl border border-gray-100 rounded-xl bg-white p-8 shadow-sm">
    <div class="mb-8 flex items-center justify-between">
      <div>
        <h2 class="text-2xl text-gray-800 font-bold">
          {{ props.initialData ? $t('page.profile.common.edit') : $t('page.profile.common.add') }}
        </h2>
        <p class="mt-1 text-sm text-gray-500">{{ $t('page.profile.common.requiredDesc') }}</p>
      </div>
      <NButton quaternary circle @click="emit('cancel')">
        <template #icon>
          <span class="text-xl">✕</span>
        </template>
      </NButton>
    </div>

    <NForm ref="formRef" :model="formData" :rules="rules" label-placement="top" require-mark-placement="right-hanging">
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
            :default-upload="false"
            :max="1"
            accept=".pdf,.md,.markdown"
            @change="handleFileChange"
            @remove="handleFileRemove"
          >
            <div
              class="upload-area cursor-pointer border-2 rounded-lg border-dashed p-8 text-center transition-all"
              :class="isDragging ? 'border-blue-500 bg-blue-50' : 'border-gray-300 hover:border-blue-400'"
              @dragover="handleDragOver"
              @dragleave="handleDragLeave"
              @drop="handleDrop"
            >
              <span class="mb-2 text-4xl" :class="isDragging ? 'text-blue-500' : 'text-gray-400'">☁️</span>
              <p class="mb-1" :class="isDragging ? 'text-blue-600 font-medium' : 'text-gray-600'">
                {{ isDragging ? $t('page.knowledge.dragUploadActive') : $t('page.knowledge.dragUpload') }}
              </p>
              <p class="text-xs text-gray-400">{{ $t('page.knowledge.uploadTip') }}</p>
            </div>
          </NUpload>
        </div>
      </NFormItem>

      <div class="mt-8 flex justify-end gap-4 border-t border-gray-100 pt-6">
        <NButton size="large" @click="emit('cancel')">{{ $t('page.profile.common.cancel') }}</NButton>
        <NButton size="large" type="primary" :loading="loading" @click="handleSubmit">
          {{ $t('page.profile.common.save') }}
        </NButton>
      </div>
    </NForm>
  </div>
</template>

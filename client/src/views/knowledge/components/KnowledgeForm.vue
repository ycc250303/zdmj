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
  <div class="nova-form">
    <header class="nova-form__head">
      <div>
        <span class="nova-eyebrow">// knowledge</span>
        <h2 class="nova-form__title">
          {{ props.initialData ? $t('page.profile.common.edit') : $t('page.profile.common.add') }}
        </h2>
        <p class="nova-form__sub">{{ $t('page.profile.common.requiredDesc') }}</p>
      </div>
      <NButton quaternary circle @click="emit('cancel')">
        <template #icon>
          <icon-carbon-close class="text-18px" />
        </template>
      </NButton>
    </header>

    <NForm
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-placement="top"
      require-mark-placement="right-hanging"
      class="nova-form__body"
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

      <NFormItem :label="$t('page.knowledge.uploadFile')" path="content">
        <div class="w-full">
          <NUpload
            :file-list="fileList"
            :max="1"
            accept=".pdf,.md,.markdown"
            @change="handleFileChange"
            @remove="handleFileRemove"
          >
            <div
              class="nova-drop"
              :class="{ 'nova-drop--active': isDragging }"
              @dragover="handleDragOver"
              @dragleave="handleDragLeave"
              @drop="handleDrop"
            >
              <span class="nova-drop__icon">
                <icon-carbon-cloud-upload class="text-28px" />
              </span>
              <p class="nova-drop__label">
                {{ isDragging ? $t('page.knowledge.dragUploadActive') : $t('page.knowledge.dragUpload') }}
              </p>
              <p class="nova-drop__hint">{{ $t('page.knowledge.uploadTip') }}</p>
            </div>
          </NUpload>
        </div>
      </NFormItem>

      <footer class="nova-form__footer">
        <NButton size="large" @click="emit('cancel')">{{ $t('page.profile.common.cancel') }}</NButton>
        <NButton size="large" type="primary" :loading="loading" @click="handleSubmit">
          {{ $t('page.profile.common.save') }}
        </NButton>
      </footer>
    </NForm>
  </div>
</template>

<style scoped>
.nova-form {
  max-width: 720px;
  margin: 0 auto;
  padding: 28px 32px 32px;
  border-radius: 22px;
  border: 1px solid var(--nova-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.05) 0%, rgba(255, 255, 255, 0.012) 100%);
  box-shadow: 0 28px 80px -36px rgba(10, 12, 30, 0.7);
  color: var(--nova-text);
}

.nova-form__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
}

.nova-eyebrow {
  display: inline-block;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--nova-violet);
  text-transform: lowercase;
  margin-bottom: 4px;
}

.nova-form__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
  background: linear-gradient(120deg, #fff 0%, #c9c4ff 60%, #93f1ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.nova-form__sub {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--nova-text-faded);
  letter-spacing: 0.02em;
}

.nova-form__body :deep(.n-form-item-label) {
  color: var(--nova-text);
  font-weight: 500;
}

.nova-form__body :deep(.n-form-item-label__text) {
  color: var(--nova-text);
}

.nova-form__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 28px;
  padding-top: 22px;
  border-top: 1px solid var(--nova-border);
}

.nova-drop {
  position: relative;
  border: 1.5px dashed var(--nova-border-strong);
  border-radius: 16px;
  padding: 36px 24px;
  text-align: center;
  background: rgba(255, 255, 255, 0.025);
  transition: border-color 0.2s ease, background 0.2s ease, transform 0.2s ease;
  cursor: pointer;
}

.nova-drop:hover {
  border-color: rgba(124, 92, 255, 0.55);
  background: rgba(124, 92, 255, 0.06);
}

.nova-drop--active {
  border-color: var(--nova-violet);
  background: rgba(124, 92, 255, 0.1);
  transform: translateY(-1px);
}

.nova-drop__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: rgba(124, 92, 255, 0.12);
  border: 1px solid rgba(124, 92, 255, 0.3);
  color: var(--nova-violet);
  margin-bottom: 10px;
}

.nova-drop--active .nova-drop__icon {
  background: rgba(124, 92, 255, 0.22);
  border-color: rgba(124, 92, 255, 0.55);
}

.nova-drop__label {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--nova-text);
}

.nova-drop--active .nova-drop__label {
  color: #fff;
}

.nova-drop__hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--nova-text-faded);
  letter-spacing: 0.02em;
}
</style>

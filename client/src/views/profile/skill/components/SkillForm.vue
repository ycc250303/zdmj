<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue';
import type { FormInst, FormRules } from 'naive-ui';
import { fetchAddSkill, fetchUpdateSkill } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';

interface Props {
  initialData?: ResumeApi.SkillUpdate;
}
const props = defineProps<Props>();
const emit = defineEmits(['success', 'cancel']);

const formRef = ref<FormInst | null>(null);
const loading = ref(false);

const formData = reactive<ResumeApi.SkillCreate>({
  name: '',
  content: [{ type: '', content: [] }] // 至少提供一个默认的空分类
});

watch(() => props.initialData, (newVal) => {
  if (newVal) {
    formData.name = newVal.name;
    // 深拷贝以防响应式污染
    formData.content = newVal.content ? JSON.parse(JSON.stringify(newVal.content)) : [{ type: '', content: [] }];
  }
}, { immediate: true });

const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: $t('page.profile.skill.name'), trigger: 'blur' }]
}));

function handleAddCategory() {
  formData.content.push({ type: '', content: [] });
}

function handleRemoveCategory(index: number) {
  formData.content.splice(index, 1);
}

async function handleSubmit() {
  try {
    await formRef.value?.validate();
    
    // 清理空数据：过滤掉分类名为空，或者没有填任何技能标签的项
    const cleanedContent = formData.content.filter(item => item.type.trim() !== '' && item.content.length > 0);
    
    if (cleanedContent.length === 0) {
      window.$message?.warning('至少填写一项有效的技能分类和详情');
      return;
    }

    loading.value = true;
    const payload = { ...formData, content: cleanedContent };

    if (props.initialData?.id) {
      const { error } = await fetchUpdateSkill({ ...payload, id: props.initialData.id });
      if (!error) {
        window.$message?.success($t('page.profile.skill.updateSuccess'));
        emit('success');
      }
    } else {
      const { error } = await fetchAddSkill(payload);
      if (!error) {
        window.$message?.success($t('page.profile.skill.addSuccess'));
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
      <NFormItem :label="$t('page.profile.skill.name')" path="name">
        <NInput v-model:value="formData.name" :placeholder="$t('page.profile.skill.namePlaceholder')" size="large" />
      </NFormItem>

      <div class="mt-6 border border-gray-200 rounded-lg p-4 bg-gray-50/50">
        <div v-for="(item, index) in formData.content" :key="index" class="relative bg-white p-4 mb-4 rounded border border-gray-100 shadow-sm">
          
          <NButton 
            v-if="formData.content.length > 1" 
            text
            class="absolute right-2 top-2" 
            @click="handleRemoveCategory(index)"
          >
            <div class="i-mdi-close-circle text-red-500 text-2xl hover:text-red-600 transition-colors"></div>
          </NButton>
          
          <NFormItem :label="$t('page.profile.skill.category')" :path="`content[${index}].type`">
            <NInput v-model:value="item.type" :placeholder="$t('page.profile.skill.categoryPlaceholder')" />
          </NFormItem>
          
          <NFormItem :label="$t('page.profile.skill.items')">
            <NDynamicTags v-model:value="item.content" />
          </NFormItem>
        </div>

        <NButton dashed block @click="handleAddCategory">
          <template #icon><div class="i-mdi-plus"></div></template>
          {{ $t('page.profile.skill.addCategory') }}
        </NButton>
      </div>

      <div class="flex justify-end gap-4 mt-8 pt-6 border-t border-gray-100">
        <NButton size="large" @click="emit('cancel')">{{ $t('page.profile.common.cancel') }}</NButton>
        <NButton size="large" type="primary" :loading="loading" @click="handleSubmit">
          {{ $t('page.profile.common.save') }}
        </NButton>
      </div>
    </NForm>
  </div>
</template>
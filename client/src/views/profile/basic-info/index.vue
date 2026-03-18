<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import type { FormInst, FormRules } from 'naive-ui';
import { fetchUpdateUserInfo } from '@/service/api/resume'; 
import type { UserUpdateDTO } from '@/service/api/resume'; 
import { useAuthStore } from '@/store/modules/auth';
import { $t } from '@/locales';

const authStore = useAuthStore();
const formRef = ref<FormInst | null>(null);
const loading = ref(false);

const formData = reactive<UserUpdateDTO>({
  name: '',
  phone: '',
  homepageUrl: ''
});

// 使用 computed 保证语言切换时校验规则同步
const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: $t('page.profile.basicInfo.namePlaceholder'), trigger: 'blur' }],
  phone: [
    { required: true, message: $t('page.profile.basicInfo.phonePlaceholder'), trigger: 'blur' },
    // 简单的中国大陆手机号正则，如果需要支持国际号码可去掉 pattern 或修改
    { pattern: /^1[3-9]\d{9}$/, message: '手机号码格式不正确', trigger: 'blur' }
  ]
}));

onMounted(() => {
  const userInfo = authStore.userInfo as any;
  if (userInfo) {
    formData.name = userInfo.name || '';
    formData.phone = userInfo.phone || '';
    formData.homepageUrl = userInfo.website || '';
  }
});

async function handleSubmit() {
  try {
    await formRef.value?.validate();
    loading.value = true;
    
    const payload = { ...formData };
    if (!payload.homepageUrl) delete payload.homepageUrl;

    const { error, data } = await fetchUpdateUserInfo(payload);
    
    if (!error) {
      window.$message?.success($t('page.profile.basicInfo.updateSuccess'));
      if (data) {
        Object.assign(authStore.userInfo as any, data);
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
  <div class="p-6 h-full overflow-y-auto bg-gray-50">
    <div class="max-w-3xl mx-auto bg-white p-8 rounded-xl border border-gray-100 shadow-sm mt-4">
      
      <div class="mb-8 pb-6 border-b border-gray-100">
        <h1 class="text-2xl font-bold text-gray-800">{{ $t('page.profile.basicInfo.title') }}</h1>
        <p class="text-gray-500 mt-2 text-sm">{{ $t('page.profile.basicInfo.desc') }}</p>
      </div>

      <NForm ref="formRef" :model="formData" :rules="rules" label-placement="top" require-mark-placement="right-hanging">
        
        <NGrid :x-gap="24" :cols="2">
          <NGridItem>
            <NFormItem :label="$t('page.profile.basicInfo.name')" path="name">
              <NInput v-model:value="formData.name" :placeholder="$t('page.profile.basicInfo.namePlaceholder')" size="large">
                <template #prefix>
                  <div class="i-mdi-account-outline text-gray-400 text-lg"></div>
                </template>
              </NInput>
            </NFormItem>
          </NGridItem>
          
          <NGridItem>
            <NFormItem :label="$t('page.profile.basicInfo.phone')" path="phone">
              <NInput v-model:value="formData.phone" :placeholder="$t('page.profile.basicInfo.phonePlaceholder')" size="large">
                <template #prefix>
                  <div class="i-mdi-phone-outline text-gray-400 text-lg"></div>
                </template>
              </NInput>
            </NFormItem>
          </NGridItem>
        </NGrid>

        <NFormItem :label="$t('page.profile.basicInfo.homepageUrl')" path="homepageUrl">
          <NInput v-model:value="formData.homepageUrl" :placeholder="$t('page.profile.basicInfo.homepageUrlPlaceholder')" size="large">
            <template #prefix>
              <div class="i-mdi-link-variant text-gray-400 text-lg"></div>
            </template>
          </NInput>
        </NFormItem>

        <div class="flex justify-end mt-8 pt-2">
          <NButton size="large" type="primary" :loading="loading" class="px-8" @click="handleSubmit">
            {{ $t('page.profile.common.save') }}
          </NButton>
        </div>

      </NForm>
    </div>
  </div>
</template>
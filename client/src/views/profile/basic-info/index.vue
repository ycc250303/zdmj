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

const formData = reactive<UserUpdateDTO>({ name: '', phone: '', homepageUrl: '' });

const rules = computed<FormRules>(() => ({
  name: [{ required: true, message: $t('page.profile.basicInfo.namePlaceholder'), trigger: 'blur' }],
  phone: [
    { required: true, message: $t('page.profile.basicInfo.phonePlaceholder'), trigger: 'blur' },
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
        const cachedUserStr = window.localStorage.getItem('custom_user_info');
        if (cachedUserStr) {
          try {
            const cachedUser = JSON.parse(cachedUserStr);
            cachedUser.name = data.name || cachedUser.name;
            cachedUser.phone = data.phone || cachedUser.phone;
            cachedUser.website = data.website || cachedUser.website;
            window.localStorage.setItem('custom_user_info', JSON.stringify(cachedUser));
            if (authStore.userInfo) {
              (authStore.userInfo as any).name = cachedUser.name;
              (authStore.userInfo as any).phone = cachedUser.phone;
              (authStore.userInfo as any).website = cachedUser.website;
            }
          } catch (e) { console.error('更新localStorage用户信息失败', e); }
        }
      }
    }
  } catch (errors) { console.warn('Validate failed', errors); }
  finally { loading.value = false; }
}
</script>

<template>
  <div class="editorial-page">
    <div class="editorial-wrap">
      <header class="section-head">
        <div class="head-meta"><span class="head-bar"></span><span class="head-tag">— PROFILE / BASIC INFORMATION —</span></div>
        <h1 class="head-title font-display">{{ $t('page.profile.basicInfo.title') }}</h1>
        <p class="head-desc">{{ $t('page.profile.basicInfo.desc') }}</p>
      </header>
      <div class="head-rule"></div>

      <NForm ref="formRef" :model="formData" :rules="rules" label-placement="top" require-mark-placement="right-hanging">
        <div class="form-grid">
          <NFormItem :label="$t('page.profile.basicInfo.name')" path="name">
            <NInput v-model:value="formData.name" :placeholder="$t('page.profile.basicInfo.namePlaceholder')" size="large" />
          </NFormItem>
          <NFormItem :label="$t('page.profile.basicInfo.phone')" path="phone">
            <NInput v-model:value="formData.phone" :placeholder="$t('page.profile.basicInfo.phonePlaceholder')" size="large" />
          </NFormItem>
        </div>
        <NFormItem :label="$t('page.profile.basicInfo.homepageUrl')" path="homepageUrl">
          <NInput v-model:value="formData.homepageUrl" :placeholder="$t('page.profile.basicInfo.homepageUrlPlaceholder')" size="large" />
        </NFormItem>
        <div class="form-footer">
          <button class="primary-btn" :disabled="loading" @click="handleSubmit">{{ loading ? '保存中...' : $t('page.profile.common.save') }}</button>
        </div>
      </NForm>
    </div>
  </div>
</template>

<style scoped>
.editorial-page { min-height: 100%; background: #fefefe; padding: 40px 56px 56px; overflow: auto; }
.editorial-wrap { max-width: 700px; }
.section-head { margin-bottom: 16px; }
.head-meta { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.head-bar { width: 40px; height: 2px; background: #c4a46c; }
.head-tag { font-size: 11px; letter-spacing: 0.22em; color: #6a6a6a; }
.head-title { font-size: clamp(32px, 4vw, 48px); font-weight: 600; letter-spacing: -0.02em; color: #1a1a1a; }
.head-desc { font-size: 14px; color: #888; margin-top: 8px; font-style: italic; }
.head-rule { height: 1px; background: #e0e0e0; margin-bottom: 40px; }
.head-rule::after { content: ''; display: block; height: 1px; background: #e0e0e0; margin-top: 4px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 24px; }
@media (max-width: 480px) { .form-grid { grid-template-columns: 1fr; } }
.form-footer { display: flex; justify-content: flex-end; margin-top: 24px; }
.primary-btn { display: inline-flex; align-items: center; gap: 8px; padding: 10px 24px; border-radius: 999px; background: #c4a46c; color: #fff; border: none; font-size: 14px; font-weight: 500; cursor: pointer; transition: all 0.25s ease; }
.primary-btn:hover { background: #a08050; transform: translateY(-1px); }
.primary-btn:disabled { opacity: 0.6; cursor: default; transform: none; }
</style>

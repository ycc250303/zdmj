// src/store/modules/resumeStore.ts
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { 
  fetchGetResumeFullContentDetail, 
  fetchUpdateProject, 
  fetchUpdateCareer, 
  fetchUpdateEducation, 
  fetchUpdateSkill, 
  fetchUpdateResume,
  fetchUpdateUserInfo, // 新增：引入更新用户信息的接口
  ResumeApi 
} from '@/service/api/resume';
import { useAuthStore } from '@/store/modules/auth'; // 新增：引入全局用户信息 Store
import { $t } from '@/locales';

export const useResumeStore = defineStore('resume-editor', () => {
  const resumeData = ref<ResumeApi.ResumeContentDTO | null>(null);
  const isLoading = ref(false);
  const isSaving = ref(false);

  // 基础信息 (增加了 homepageUrl)
  const personalInfo = ref({
    fullName: '',
    phone: '',
    email: '',
    major: '', // 注意：后端目前没有 major 字段，这部分暂存前端，如果要保存需后端在 User 实体加字段
    homepageUrl: '' 
  });

  async function initResumeData(resumeId: number) {
    isLoading.value = true;
    try {
      const { data } = await fetchGetResumeFullContentDetail(resumeId);
      if (data) resumeData.value = JSON.parse(JSON.stringify(data));

      const authStore = useAuthStore();
      if (authStore.userInfo) {
        const user = authStore.userInfo as any;
        personalInfo.value.fullName = user.name || user.username || '';
        personalInfo.value.phone = user.phone || '';
        personalInfo.value.email = user.email || '';
        personalInfo.value.homepageUrl = user.website || user.homepageUrl || '';
      }
    } catch (error) {
      console.error('获取简历失败', error);
    } finally {
      isLoading.value = false;
    }
  }

  async function saveAllData() {
    if (!resumeData.value) return;
    isSaving.value = true;
    try {
      const updatePromises = [];

      // 简历外壳
      updatePromises.push(fetchUpdateResume({ 
        id: resumeData.value.id, 
        name: resumeData.value.name, 
        skillId: resumeData.value.skill?.id || 1 
      }));

      const userPayload = {
        name: personalInfo.value.fullName,
        phone: personalInfo.value.phone,
        homepageUrl: personalInfo.value.homepageUrl
      };
  
      const userInfoPromise = fetchUpdateUserInfo(userPayload).then(res => {
        if (res.data) {
          const authStore = useAuthStore();
          Object.assign(authStore.userInfo as any, res.data);
        }
      });
      updatePromises.push(userInfoPromise);

      // 更新所有内容模块
      if (resumeData.value.projects?.length) {
        updatePromises.push(...resumeData.value.projects.map(p => fetchUpdateProject(p)));
      }
      if (resumeData.value.careers?.length) {
        updatePromises.push(...resumeData.value.careers.map(c => fetchUpdateCareer(c)));
      }
      if (resumeData.value.educations?.length) {
        updatePromises.push(...resumeData.value.educations.map(e => fetchUpdateEducation(e)));
      }
      
      await Promise.all(updatePromises);
      window.$message?.success($t('page.resume.saveSuccess', '所有简历修改已同步至云端！'));
    } catch (e) {
      console.error('保存失败:', e);
      window.$message?.error($t('page.resume.saveFail', '保存失败，请检查网络'));
    } finally {
      isSaving.value = false;
    }
  }

  return {
    resumeData,
    personalInfo,
    isLoading,
    isSaving,
    initResumeData,
    saveAllData
  };
});
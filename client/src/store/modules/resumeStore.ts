// src/store/modules/resumeStore.ts
import { defineStore } from 'pinia';
import { ref } from 'vue';
import {
  fetchGetResumeMeContent,
  fetchSaveResumeMeContent,
  fetchUpdateUserInfo,
  ResumeApi
} from '@/service/api/resume';
import { useAuthStore } from '@/store/modules/auth';
import { $t } from '@/locales';

export const useResumeStore = defineStore('resume-editor', () => {
  const resumeData = ref<ResumeApi.ResumeContentDTO | null>(null);
  const isLoading = ref(false);
  const isSaving = ref(false);

  const personalInfo = ref({
    fullName: '',
    phone: '',
    email: '',
    major: '',
    homepageUrl: ''
  });

  async function initResumeData(_resumeId?: number) {
    isLoading.value = true;
    try {
      const { data, error } = await fetchGetResumeMeContent();
      if (error || !data) throw new Error('简历不存在');

      resumeData.value = data as ResumeApi.ResumeContentDTO;

      // 从 personalInfo 填充编辑器的个人信息
      if (data.personalInfo) {
        personalInfo.value.fullName = data.personalInfo.name || '';
        personalInfo.value.phone = data.personalInfo.phone || '';
        personalInfo.value.homepageUrl = data.personalInfo.homepageUrl || '';
      }

      const authStore = useAuthStore();
      if (authStore.userInfo && !data.personalInfo) {
        const user = authStore.userInfo as any;
        personalInfo.value.fullName = user.name || user.username || '';
        personalInfo.value.phone = user.phone || '';
        personalInfo.value.email = user.email || '';
        personalInfo.value.homepageUrl = user.website || user.homepageUrl || '';
      }
    } catch (error) {
      console.error('获取简历数据失败', error);
      window.$message?.error('拉取简历失败，请刷新重试');
    } finally {
      isLoading.value = false;
    }
  }

  async function saveAllData() {
    if (!resumeData.value) return;
    isSaving.value = true;
    try {
      // 新接口：全量保存
      const { error } = await fetchSaveResumeMeContent(resumeData.value);
      if (error) throw error;

      // 同步更新用户信息
      await fetchUpdateUserInfo({
        name: personalInfo.value.fullName,
        phone: personalInfo.value.phone,
        homepageUrl: personalInfo.value.homepageUrl
      }).then(res => {
        if (res.data) {
          Object.assign(useAuthStore().userInfo as any, res.data);
        }
      });

      window.$message?.success($t('page.resume.saveSuccess', '所有简历修改已同步至云端！'));
    } catch (e) {
      console.error('保存失败:', e);
      window.$message?.error($t('page.resume.saveFail', '保存失败，请检查网络'));
    } finally {
      isSaving.value = false;
    }
  }

  // 便捷方法：直接修改 resumeData 中的子数组
  function addEducation(data: ResumeApi.EducationCreate) {
    if (!resumeData.value) return false;
    resumeData.value.educations.push({ id: Date.now(), ...data } as any);
    return true;
  }
  function removeEducation(idx: number) {
    resumeData.value?.educations.splice(idx, 1);
  }
  function addProject(data: ResumeApi.ProjectCreate) {
    if (!resumeData.value) return false;
    resumeData.value.projects.push({ id: Date.now(), ...data } as any);
    return true;
  }
  function removeProject(idx: number) {
    resumeData.value?.projects.splice(idx, 1);
  }
  function addCareer(data: ResumeApi.CareerCreate) {
    if (!resumeData.value) return false;
    resumeData.value.careers.push({ id: Date.now(), ...data } as any);
    return true;
  }
  function removeCareer(idx: number) {
    resumeData.value?.careers.splice(idx, 1);
  }

  return {
    resumeData, isLoading, isSaving, personalInfo,
    initResumeData, saveAllData,
    addEducation, removeEducation,
    addProject, removeProject,
    addCareer, removeCareer,
  };
});

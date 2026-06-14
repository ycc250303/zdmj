import { defineStore } from 'pinia';
import { ref } from 'vue';
import { fetchGetResumeMeContent } from '@/service/api/resume';

export const useResumeLegacyStore = defineStore('resume-legacy', () => {
  const loading = ref(false);
  const resumeData = ref<any>({ basicInfo: {}, educations: [], projects: [] });

  async function initResume(_id?: number) {
    loading.value = true;
    try {
      const { data, error } = await fetchGetResumeMeContent();
      if (!error && data) {
        resumeData.value.educations = data.educations || [];
        resumeData.value.projects = data.projects || [];
        resumeData.value.basicInfo = data.personalInfo || { name: '', phone: '', email: '' };
      }
    } catch (err) {
      window.$message?.error('获取简历内容失败，请检查网络');
    } finally {
      loading.value = false;
    }
  }

  return { loading, resumeData, initResume };
});
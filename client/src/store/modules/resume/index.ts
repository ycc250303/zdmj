import { defineStore } from 'pinia';
import { ref } from 'vue';
import { fetchGetResumeFullContentDetail } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';

export const useResumeStore = defineStore('resume-editor', () => {
  // 定义响应式状态
  const loading = ref(false);
  
  const resumeData = ref<any>({
    basicInfo: {},
    educations: [],
    projects: []
  });

  // 定义获取数据的 Action
  async function initResume(id: number) {
    loading.value = true;
    try {
      const { data, error } = await fetchGetResumeFullContentDetail(id);
      
      if (!error && data) {
        resumeData.value.educations = data.educations || [];
        resumeData.value.projects = data.projects || [];
        resumeData.value.basicInfo = data.basicInfo || { 
          name: '', 
          intention: '', 
          phone: '', 
          email: '' 
        };
      
      }
    } catch (err) {
      window.$message?.error('获取简历内容失败，请检查网络');
    } finally {
      loading.value = false;
    }
  }

  return {
    loading,
    resumeData,
    initResume
  };
});
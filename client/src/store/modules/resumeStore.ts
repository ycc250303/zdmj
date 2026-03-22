// src/store/modules/resumeStore.ts
import { defineStore } from 'pinia';
import { ref } from 'vue';
import { 
  fetchGetResumeDetail,        // 只拉取简历的外壳（名称、绑定的技能ID）
  fetchUpdateResume,
  fetchGetEducationList,       // 拉取全局教育经历池
  fetchUpdateEducation, 
  fetchGetProjectList,         // 拉取全局项目经历池
  fetchUpdateProject, 
  fetchGetCareerList,          // 拉取全局实习经历池
  fetchUpdateCareer, 
  fetchGetSkillList,           // 拉取技能池
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

  async function initResumeData(resumeId: number) {
    isLoading.value = true;
    try {
      const resumeRes = await fetchGetResumeDetail(resumeId);
      if (!resumeRes.data) throw new Error('简历不存在');
      const baseResume = resumeRes.data;

      const [eduRes, projRes, careerRes, skillRes] = await Promise.all([
        fetchGetEducationList(),
        fetchGetProjectList(),
        fetchGetCareerList(),
        fetchGetSkillList()
      ]);

      const currentSkill = skillRes.data?.find((s:any) => s.id === baseResume.skillId) 
        || skillRes.data?.[0] 
        || { id: baseResume.skillId, name: '默认技能', content: [] };

      resumeData.value = {
        id: baseResume.id,
        name: baseResume.name,
        skill: currentSkill as ResumeApi.SkillDTO,
        educations: eduRes.data || [],
        projects: projRes.data || [],
        careers: careerRes.data || []
      };

      const authStore = useAuthStore();
      if (authStore.userInfo) {
        const user = authStore.userInfo as any;
        personalInfo.value.fullName = user.name || user.username || '';
        personalInfo.value.phone = user.phone || '';
        personalInfo.value.email = user.email || '';
        personalInfo.value.homepageUrl = user.website || user.homepageUrl || '';
      }
    } catch (error) {
      console.error('获取全局信息失败', error);
      window.$message?.error('拉取全局经历失败，请刷新重试');
    } finally {
      isLoading.value = false;
    }
  }

  async function saveAllData() {
    if (!resumeData.value) return;
    isSaving.value = true;
    try {
      const updatePromises = [];

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
      updatePromises.push(
        fetchUpdateUserInfo(userPayload).then(res => {
          if (res.data) {
            const authStore = useAuthStore();
            Object.assign(authStore.userInfo as any, res.data);
          }
        })
      );

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
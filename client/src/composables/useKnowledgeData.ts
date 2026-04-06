import { ref } from 'vue';
import { fetchGetProjectList } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';

/**
 * 知识库公共逻辑Composable
 * 提供项目列表管理和相关工具函数
 */
export function useKnowledgeData() {
  const projectList = ref<ResumeApi.ProjectDTO[]>([]);
  const projectLoading = ref(false);

  /**
   * 加载项目列表
   */
  async function loadProjects() {
    projectLoading.value = true;
    try {
      const { data, error } = await fetchGetProjectList();
      if (!error && data) {
        projectList.value = data;
      }
    } finally {
      projectLoading.value = false;
    }
  }

  /**
   * 根据项目ID获取项目名称
   */
  function getProjectName(projectId: number): string {
    const project = projectList.value.find(p => p.id === projectId);
    return project?.name || `项目 ${projectId}`;
  }

  /**
   * 解析标签字段
   * 处理后端可能返回的字符串或数组格式
   */
  function parseTag(tag: string[] | string | undefined): string[] {
    if (!tag) return [];
    if (Array.isArray(tag)) return tag;
    try {
      const parsed = JSON.parse(tag);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }

  /**
   * 判断是否为GitHub URL
   */
  function isGitHubUrl(url: string): boolean {
    return url.includes('github.com');
  }

  /**
   * 判断是否为PDF URL
   */
  function isPdfUrl(url: string): boolean {
    return url.toLowerCase().includes('.pdf');
  }

  return {
    projectList,
    projectLoading,
    loadProjects,
    getProjectName,
    parseTag,
    isGitHubUrl,
    isPdfUrl
  };
}

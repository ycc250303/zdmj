<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { fetchGetCurrentCapabilityProfile, fetchGenerateCapabilityProfile } from '@/service/api/profile';
import type { CapabilityProfileApi } from '@/service/api/profile';
import { fetchGetResumeFullContentList } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';
import { useAuthStore } from '@/store/modules/auth';

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(true);
const generating = ref(false);
const profile = ref<CapabilityProfileApi.StudentCapabilityProfile | null>(null);
const isLogin = computed(() => Boolean(authStore.token));

// 计算总分项
const totalScore = computed(() => {
  if (!profile.value?.scoreDetail) return 0;
  const { scoreDetail } = profile.value;
  return (
    (scoreDetail.jobMatchTechDepthScore || 0) +
    (scoreDetail.projectPracticeScore || 0) +
    (scoreDetail.contentCompletenessScore || 0) +
    (scoreDetail.structureExpressionScore || 0) +
    (scoreDetail.professionalPotentialScore || 0)
  );
});

// 能力项列表
const abilityItems = computed(() => {
  if (!profile.value) return [];
  return [
    { key: 'professionalSkills', label: '专业技能', icon: 'mdi:code-braces', value: profile.value.professionalSkills },
    { key: 'certificates', label: '证书', icon: 'mdi:certificate', value: profile.value.certificates },
    { key: 'innovationAbility', label: '创新能力', icon: 'mdi:lightbulb-outline', value: profile.value.innovationAbility },
    { key: 'learningAbility', label: '学习能力', icon: 'mdi:school-outline', value: profile.value.learningAbility },
    { key: 'pressureResistance', label: '抗压能力', icon: 'mdi:weight-lifter', value: profile.value.pressureResistance },
    { key: 'communicationAbility', label: '沟通能力', icon: 'mdi:account-group-outline', value: profile.value.communicationAbility },
    { key: 'practicalAbility', label: '实习/实践能力', icon: 'mdi:briefcase-outline', value: profile.value.practicalAbility }
  ];
});

async function loadData() {
  loading.value = true;

  try {
    // 调用API检查是否已生成过画像
    const { data, error } = await fetchGetCurrentCapabilityProfile();

    if (!error && data) {
      // 已生成过，显示数据
      profile.value = data;
    } else {
      // 未生成过或其他错误，显示空状态（不报错）
      profile.value = null;
    }
  } catch (err) {
    // 任何异常都静默处理，显示空状态
    profile.value = null;
  } finally {
    loading.value = false;
  }
}

/**
 * 从简历数据拼接成文本
 */
function buildResumeText(resumes: ResumeApi.ResumeContentDTO[]): string {
  if (!resumes || resumes.length === 0) {
    return '';
  }

  const resume = resumes[0]; // 取第一份简历
  const parts: string[] = [];

  // 基本信息
  parts.push('# 个人简历\n');

  // 技能
  if (resume.skill) {
    parts.push('## 专业技能');
    resume.skill.content.forEach((group: any) => {
      parts.push(`### ${group.type}`);
      parts.push(group.content.join('、'));
    });
    parts.push('');
  }

  // 教育经历
  if (resume.educations && resume.educations.length > 0) {
    parts.push('## 教育经历');
    resume.educations.forEach(edu => {
      parts.push(`### ${edu.school}`);
      parts.push(`专业：${edu.major}`);
      parts.push(`学历：${edu.degree}`);
      parts.push(`时间：${edu.startDate} - ${edu.endDate || '至今'}`);
      if (edu.gpa) parts.push(`GPA：${edu.gpa}`);
      parts.push('');
    });
  }

  // 工作/实习经历
  if (resume.careers && resume.careers.length > 0) {
    parts.push('## 工作/实习经历');
    resume.careers.forEach(career => {
      parts.push(`### ${career.company}`);
      parts.push(`职位：${career.position}`);
      parts.push(`时间：${career.startDate} - ${career.endDate || '至今'}`);
      if (career.details) parts.push(`职责：${career.details}`);
      parts.push('');
    });
  }

  // 项目经历
  if (resume.projects && resume.projects.length > 0) {
    parts.push('## 项目经历');
    resume.projects.forEach(project => {
      parts.push(`### ${project.name}`);
      parts.push(`角色：${project.role}`);
      parts.push(`时间：${project.startDate} - ${project.endDate || '至今'}`);
      parts.push(`描述：${project.description}`);
      parts.push(`贡献：${project.contribution}`);
      if (project.techStack) parts.push(`技术栈：${project.techStack.join('、')}`);
      if (project.highlights) parts.push(`亮点：${project.highlights}`);
      parts.push('');
    });
  }

  return parts.join('\n');
}

async function handleRegenerate() {
  generating.value = true;

  try {
    // 1. 获取用户的简历数据
    const { data: resumes, error: resumeError } = await fetchGetResumeFullContentList();

    if (resumeError || !resumes || resumes.length === 0) {
      window.$message?.warning('请先完善简历信息（教育经历、项目经历、技能等）后再生成能力画像');
      return;
    }

    // 2. 拼接简历文本
    const resumeText = buildResumeText(resumes);

    if (!resumeText || resumeText.length < 100) {
      window.$message?.warning('简历信息过少，请至少填写教育经历、项目经历或技能信息后再生成能力画像');
      return;
    }

    // 3. 调用生成API（注意：AI生成需要较长时间，请耐心等待）
    const { data, error } = await fetchGenerateCapabilityProfile({ rawText: resumeText });

    if (!error && data) {
      profile.value = data;
      window.$message?.success('生成能力画像成功');
    } else {
      console.error('生成能力画像失败，错误信息:', error);
      window.$message?.error('生成能力画像失败: ' + (error?.message || '未知错误'));
    }
  } catch (err: any) {
    console.error('生成能力画像异常:', err);
    window.$message?.error('生成能力画像异常: ' + (err?.message || '请检查网络连接'));
  } finally {
    generating.value = false;
  }
}

function getScoreColor(score: number): string {
  if (score >= 80) return '#52c41a';
  if (score >= 60) return '#faad14';
  return '#f5222d';
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="p-6 h-full overflow-y-auto bg-gray-50">
    <!-- 未登录提示 -->
    <div v-if="!isLogin" class="max-w-4xl mx-auto text-center py-20 bg-white rounded-xl border border-gray-100 shadow-sm">
      <div class="i-mdi-lock-outline text-6xl mb-4 mx-auto opacity-50 text-gray-400"></div>
      <p class="text-gray-500 mb-4">请先登录后查看能力画像</p>
      <NButton type="primary" @click="router.push('/login')">
        前往登录
      </NButton>
    </div>

    <!-- 已登录但无数据 -->
    <NSpin v-else :show="loading">
      <div v-if="profile" class="max-w-6xl mx-auto space-y-6">
        <!-- 头部：标题和操作按钮 -->
        <div class="flex justify-between items-center bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
          <div>
            <h1 class="text-2xl font-bold text-gray-800">{{ $t('page.profile.capability.title') }}</h1>
            <p class="text-gray-500 mt-2 text-sm">{{ $t('page.profile.capability.desc') }}</p>
          </div>
          <NButton type="primary" size="large" :loading="generating" @click="handleRegenerate">
            <template #icon>
              <div class="i-mdi-refresh"></div>
            </template>
            {{ $t('page.profile.capability.regenerate') }}
          </NButton>
        </div>

        <!-- 一句话总结 -->
        <div v-if="profile.summary" class="bg-gradient-to-r from-blue-50 to-indigo-50 p-6 rounded-xl border border-blue-100">
          <div class="flex items-start gap-3">
            <div class="i-mdi-lightbulb text-2xl text-blue-600 mt-1"></div>
            <div>
              <h3 class="font-semibold text-gray-800 mb-2">{{ $t('page.profile.capability.summary') }}</h3>
              <p class="text-gray-700">{{ profile.summary }}</p>
            </div>
          </div>
        </div>

        <!-- 评分概览 -->
        <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
          <!-- 综合竞争力 -->
          <div class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
            <div class="flex items-center justify-between mb-4">
              <span class="text-gray-600 font-medium">{{ $t('page.profile.capability.competitiveness') }}</span>
              <div class="i-mdi-trophy text-2xl text-yellow-500"></div>
            </div>
            <div class="text-center">
              <div class="text-4xl font-bold" :style="{ color: getScoreColor(profile.competitivenessScore || 0) }">
                {{ profile.competitivenessScore || 0 }}
              </div>
              <div class="text-sm text-gray-500 mt-1">综合竞争力评分</div>
            </div>
          </div>

          <!-- 简历完整度 -->
          <div class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
            <div class="flex items-center justify-between mb-4">
              <span class="text-gray-600 font-medium">{{ $t('page.profile.capability.completeness') }}</span>
              <div class="i-mdi-file-check-outline text-2xl text-green-500"></div>
            </div>
            <div class="text-center">
              <div class="text-4xl font-bold" :style="{ color: getScoreColor(profile.completenessScore || 0) }">
                {{ profile.completenessScore || 0 }}
              </div>
              <div class="text-sm text-gray-500 mt-1">简历完整度评分</div>
            </div>
          </div>

          <!-- 总分 -->
          <div class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
            <div class="flex items-center justify-between mb-4">
              <span class="text-gray-600 font-medium">{{ $t('page.profile.capability.totalScore') }}</span>
              <div class="i-mdi-chart-line text-2xl text-blue-500"></div>
            </div>
            <div class="text-center">
              <div class="text-4xl font-bold" :style="{ color: getScoreColor(totalScore) }">
                {{ totalScore }}
              </div>
              <div class="text-sm text-gray-500 mt-1">五项总分</div>
            </div>
          </div>
        </div>

        <!-- 岗位专项评估分项 -->
        <div v-if="profile.scoreDetail" class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
          <h3 class="text-lg font-bold text-gray-800 mb-4">{{ $t('page.profile.capability.scoreDetail') }}</h3>
          <div class="grid grid-cols-1 md:grid-cols-5 gap-4">
            <div v-for="(value, key) in profile.scoreDetail" :key="key" class="text-center p-4 bg-gray-50 rounded-lg">
              <div class="text-2xl font-bold mb-1" :style="{ color: getScoreColor(value as number || 0) }">
                {{ value || 0 }}
              </div>
              <div class="text-xs text-gray-500">
                {{
                  key === 'jobMatchTechDepthScore'
                    ? '技术深度'
                    : key === 'projectPracticeScore'
                      ? '项目实践'
                      : key === 'contentCompletenessScore'
                        ? '内容完整度'
                        : key === 'structureExpressionScore'
                          ? '结构表达'
                          : '职业素养'
                }}
              </div>
            </div>
          </div>
        </div>

        <!-- 能力详情 -->
        <div class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
          <h3 class="text-lg font-bold text-gray-800 mb-4">{{ $t('page.profile.capability.abilityDetail') }}</h3>
          <div class="space-y-4">
            <div v-for="item in abilityItems" :key="item.key" class="border-l-4 border-blue-500 pl-4 py-2">
              <div class="flex items-center gap-2 mb-2">
                <div :class="item.icon + ' text-xl text-blue-600'"></div>
                <h4 class="font-semibold text-gray-800">{{ item.label }}</h4>
              </div>
              <p v-if="item.value" class="text-gray-600 text-sm leading-relaxed pl-7">{{ item.value }}</p>
              <p v-else class="text-gray-400 text-sm pl-7">暂无相关信息</p>
            </div>
          </div>
        </div>

        <!-- 优势、不足和建议 -->
        <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
          <!-- 优势 -->
          <div v-if="profile.strengths && profile.strengths.length > 0" class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
            <div class="flex items-center gap-2 mb-4">
              <div class="i-mdi-thumb-up-outline text-xl text-green-500"></div>
              <h3 class="font-bold text-gray-800">{{ $t('page.profile.capability.strengths') }}</h3>
            </div>
            <div class="space-y-2">
              <div v-for="(item, index) in profile.strengths" :key="index" class="flex items-start gap-2 text-sm">
                <div class="i-mdi-check-circle text-green-500 mt-0.5 flex-shrink-0"></div>
                <span class="text-gray-700">{{ item }}</span>
              </div>
            </div>
          </div>

          <!-- 缺失技能 -->
          <div v-if="profile.missingSkills && profile.missingSkills.length > 0" class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
            <div class="flex items-center gap-2 mb-4">
              <div class="i-mdi-alert-circle-outline text-xl text-orange-500"></div>
              <h3 class="font-bold text-gray-800">{{ $t('page.profile.capability.missingSkills') }}</h3>
            </div>
            <div class="space-y-2">
              <div v-for="(item, index) in profile.missingSkills" :key="index" class="flex items-start gap-2 text-sm">
                <div class="i-mdi-minus-circle text-orange-500 mt-0.5 flex-shrink-0"></div>
                <span class="text-gray-700">{{ item }}</span>
              </div>
            </div>
          </div>

          <!-- 证据不足项 -->
          <div v-if="profile.weakEvidenceItems && profile.weakEvidenceItems.length > 0" class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
            <div class="flex items-center gap-2 mb-4">
              <div class="i-mdi-help-circle-outline text-xl text-red-500"></div>
              <h3 class="font-bold text-gray-800">{{ $t('page.profile.capability.weakEvidence') }}</h3>
            </div>
            <div class="space-y-2">
              <div v-for="(item, index) in profile.weakEvidenceItems" :key="index" class="flex items-start gap-2 text-sm">
                <div class="i-mdi-alert text-red-500 mt-0.5 flex-shrink-0"></div>
                <span class="text-gray-700">{{ item }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 改进建议 -->
        <div v-if="profile.suggestions && profile.suggestions.length > 0" class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
          <div class="flex items-center gap-2 mb-4">
            <div class="i-mdi-lightbulb-on-outline text-xl text-yellow-500"></div>
            <h3 class="font-bold text-gray-800">{{ $t('page.profile.capability.suggestions') }}</h3>
          </div>
          <div class="space-y-4">
            <div v-for="(item, index) in profile.suggestions" :key="index" class="border border-gray-200 rounded-lg p-4">
              <div class="flex items-start justify-between mb-2">
                <div class="flex items-center gap-2">
                  <span class="px-2 py-1 text-xs rounded" :class="{
                    'bg-red-100 text-red-700': item.priority === '高',
                    'bg-yellow-100 text-yellow-700': item.priority === '中',
                    'bg-green-100 text-green-700': item.priority === '低'
                  }">
                    {{ item.priority }}优先级
                  </span>
                  <span class="text-sm font-medium text-gray-700">{{ item.category }}</span>
                </div>
              </div>
              <div class="text-sm text-gray-600 mb-2">
                <span class="font-medium">问题：</span>{{ item.issue }}
              </div>
              <div class="text-sm text-gray-700">
                <span class="font-medium">建议：</span>{{ item.recommendation }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="max-w-4xl mx-auto text-center py-20 bg-white rounded-xl border border-gray-100 shadow-sm">
        <div class="i-mdi-file-document-outline text-6xl mb-4 mx-auto opacity-50 text-gray-400"></div>
        <p class="text-gray-400 mb-4">{{ $t('page.profile.capability.empty') }}</p>
        <NButton type="primary" :loading="generating" @click="handleRegenerate">
          {{ $t('page.profile.capability.generate') }}
        </NButton>
      </div>
    </NSpin>
  </div>
</template>

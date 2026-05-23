<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { fetchQueryCapabilityProfile, fetchGenerateCapabilityProfile, fetchUploadFile } from '@/service/api/profile';
import type { CapabilityProfileApi } from '@/service/api/profile';
import { fetchGetResumeFullContentList } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';
import { useAuthStore } from '@/store/modules/auth';
import CapabilityScoreCard, { type Dimension } from '@/components/common/CapabilityScoreCard.vue';

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(true);
const generating = ref(false);
const uploading = ref(false);
const profile = ref<CapabilityProfileApi.StudentCapabilityProfile | null>(null);
const isLogin = computed(() => Boolean(authStore.token));

// 生成方式：'0'=自动生成（从简历数据）, '1'=文件上传, '2'=纯文本
// 使用字符串类型，因为 NTabs 的 name 属性是字符串
const generateMethod = ref<string>('0');

// 文件上传相关
const fileList = ref<any[]>([]);
const uploadedFileUrl = ref('');

// 纯文本输入
const rawTextInput = ref('');

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
    // 调用API查询画像（仅查询，不触发生成）
    const { data, error } = await fetchQueryCapabilityProfile();

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

/**
 * 处理文件上传
 */
async function handleFileUpload() {
  if (fileList.value.length === 0) {
    window.$message?.warning($t('page.profile.capability.selectFileFirst'));
    return;
  }

  uploading.value = true;

  try {
    // NUpload 的文件项结构：{ file: File, ... } 或直接是 File 对象
    const fileItem = fileList.value[0];
    const file = fileItem?.file || fileItem;

    if (!file || !file.name) {
      window.$message?.error($t('page.profile.capability.fileInvalid'));
      uploading.value = false;
      return;
    }

    // 验证文件名，检测中文字符
    const hasNonAscii = /[^\x00-\x7F]/.test(file.name);
    if (hasNonAscii) {
      window.$message?.error($t('page.profile.capability.fileNameHasChinese'));
      uploading.value = false;
      return;
    }

    // 验证文件类型
    const fileName = file.name.toLowerCase();
    const validExtensions = ['.pdf', '.txt', '.doc', '.docx'];
    const isValidExtension = validExtensions.some(ext => fileName.endsWith(ext));

    if (!isValidExtension) {
      window.$message?.error($t('page.profile.capability.onlySupportFormats'));
      uploading.value = false;
      return;
    }

    const { data, error } = await fetchUploadFile(file, 'profile');

    if (!error && data) {
      uploadedFileUrl.value = data.url;
      // 确保当前在文件上传模式
      generateMethod.value = '1';
      console.log('✅ 文件上传成功:', {
        fileName: data.fileName,
        url: data.url,
        key: data.key,
        contentType: data.contentType
      });
      console.log('✅ 已切换到文件上传模式 (generateMethod = 1)');
      window.$message?.success($t('page.profile.capability.uploadSuccess2'));
    } else {
      console.error('❌ 文件上传失败:', error);
      window.$message?.error($t('page.profile.capability.uploadFailed') + ': ' + (error?.message || $t('page.profile.capability.unknownError')));
    }
  } catch (err: any) {
    console.error('文件上传异常:', err);
    window.$message?.error($t('page.profile.capability.uploadException') + ': ' + (err?.message || $t('page.profile.capability.checkNetwork')));
  } finally {
    uploading.value = false;
  }
}

/**
 * 处理文件选择变化
 */
function handleFileChange(options: any) {
  // NUpload 的 update:file-list 事件可能直接传递数组，或者传递包含 fileList 属性的对象
  const newFileList = Array.isArray(options) ? options : (options?.fileList || []);
  fileList.value = newFileList;
  // 如果用户删除了文件，清空URL
  if (newFileList.length === 0) {
    uploadedFileUrl.value = '';
  }
}

/**
 * 生成能力画像
 */
async function handleGenerate() {
  generating.value = true;

  try {
    let requestData: CapabilityProfileApi.CapabilityProfileGenerateReq = {};

    if (generateMethod.value === '0') {
      // 自动生成（从简历数据）
      const { data: resumes, error: resumeError } = await fetchGetResumeFullContentList();

      if (resumeError || !resumes || resumes.length === 0) {
        window.$message?.warning($t('page.profile.capability.completeResumeFirst'));
        return;
      }

      const resumeText = buildResumeText(resumes);

      if (!resumeText || resumeText.length < 100) {
        window.$message?.warning($t('page.profile.capability.resumeTooShort'));
        return;
      }

      requestData = { rawText: resumeText };
    } else if (generateMethod.value === '1') {
      // 文件上传
      if (!uploadedFileUrl.value) {
        window.$message?.warning($t('page.profile.capability.pleaseUploadFile'));
        return;
      }

      // 额外验证URL格式
      try {
        new URL(uploadedFileUrl.value);
      } catch (e) {
        console.error('❌ 无效的URL:', uploadedFileUrl.value);
        window.$message?.error($t('page.profile.capability.urlInvalid'));
        return;
      }

      requestData = { pdfUrl: uploadedFileUrl.value };
      console.log('🔍 [文件上传模式] 请求参数:', JSON.stringify(requestData, null, 2));
      console.log('🔍 [文件上传模式] PDF URL:', uploadedFileUrl.value);
    } else if (generateMethod.value === '2') {
      // 纯文本输入
      if (!rawTextInput.value || rawTextInput.value.trim().length < 50) {
        window.$message?.warning($t('page.profile.capability.inputAtLeastChars'));
        return;
      }
      requestData = { rawText: rawTextInput.value.trim() };
    }

    const { data, error } = await fetchGenerateCapabilityProfile(requestData);

    if (!error && data) {
      profile.value = data;
      window.$message?.success($t('page.profile.capability.generateSuccess'));
    } else {
      console.error('生成能力画像失败，错误信息:', error);
      console.error('请求参数:', requestData);

      // 打印完整的错误响应
      if (error && typeof error === 'object' && 'response' in error) {
        const axiosError = error as any;
        const errorData = axiosError.response?.data;
        console.error('后端返回的完整错误:', errorData);
        console.error('错误状态码:', axiosError.response?.status);

        // 根据错误信息提供更具体的提示
        let errorMessage = $t('page.profile.capability.generateFailed');
        if (generateMethod.value === '1') {
          // 文件上传模式
          if (errorData?.msg?.includes('PDF') || errorData?.message?.includes('PDF')) {
            errorMessage = $t('page.profile.capability.pdfParseFailed');
          } else {
            errorMessage = $t('page.profile.capability.fileProcessFailed') + ': ' + (errorData?.msg || errorData?.message || $t('page.profile.capability.unknownError'));
          }
        } else {
          errorMessage = $t('page.profile.capability.generateFailed') + ': ' + (errorData?.msg || errorData?.message || $t('page.profile.capability.unknownError'));
        }
        window.$message?.error(errorMessage);
      } else {
        window.$message?.error($t('page.profile.capability.generateFailed') + ': ' + (error?.message || $t('page.profile.capability.unknownError')));
      }
    }
  } catch (err: any) {
    console.error('生成能力画像异常:', err);
    window.$message?.error($t('page.profile.capability.generateFailed') + ': ' + (err?.message || $t('page.profile.capability.checkNetwork')));
  } finally {
    generating.value = false;
  }
}

function getScoreColor(score: number): string {
  if (score >= 80) return '#52c41a';
  if (score >= 60) return '#faad14';
  return '#f5222d';
}

// --- 雷达图维度（参考五项评分） ---
const scoreDimensions = computed<Dimension[]>(() => {
  const sd = profile.value?.scoreDetail;
  if (!sd) return [];
  return [
    { key: 'jobMatchTechDepthScore', label: '技术深度', score: sd.jobMatchTechDepthScore || 0, max: 40 },
    { key: 'projectPracticeScore', label: '项目实践', score: sd.projectPracticeScore || 0, max: 20 },
    { key: 'contentCompletenessScore', label: '内容完整度', score: sd.contentCompletenessScore || 0, max: 15 },
    { key: 'structureExpressionScore', label: '结构表达', score: sd.structureExpressionScore || 0, max: 15 },
    { key: 'professionalPotentialScore', label: '职业素养', score: sd.professionalPotentialScore || 0, max: 10 }
  ];
});

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="p-6 h-full overflow-y-auto bg-gray-50">
    <!-- 未登录提示 -->
    <div v-if="!isLogin" class="max-w-4xl mx-auto text-center py-20 bg-white rounded-xl border border-gray-100 shadow-sm">
      <span class="text-6xl mb-4 mx-auto opacity-50 text-gray-400">🔒</span>
      <p class="text-gray-500 mb-4">{{ $t('page.profile.capability.loginToView') }}</p>
      <NButton type="primary" @click="router.push('/login')">
        {{ $t('page.profile.capability.goToLogin') }}
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
          <NButton type="primary" size="large" :loading="generating" @click="handleGenerate">
            <template #icon>
              <span>🔄</span>
            </template>
            {{ $t('page.profile.capability.regenerate') }}
          </NButton>
        </div>

        <!-- 生成方式选择和输入区域 -->
        <div class="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
          <h3 class="text-lg font-bold text-gray-800 mb-4">{{ $t('page.profile.capability.generateMethod') }}</h3>

          <!-- 生成方式Tab -->
          <NTabs v-model:value="generateMethod" type="segment">
            <!-- 自动生成 -->
            <NTabPane name="0" :tab="$t('page.profile.capability.autoGenerate')">
              <p class="text-gray-600 text-sm">{{ $t('page.profile.capability.autoGenerateDesc') }}</p>
            </NTabPane>

            <!-- 文件上传 -->
            <NTabPane name="1" :tab="$t('page.profile.capability.fileUpload')">
              <div class="space-y-4">
                <p class="text-gray-600 text-sm">{{ $t('page.profile.capability.fileUploadDesc') }}</p>
                <NAlert type="info" :bordered="false" class="text-sm">
                  <template #icon>
                    <span>ℹ️</span>
                  </template>
                  <div class="space-y-1">
                    <p>• {{ $t('page.profile.capability.uploadTips.useEnglishName') }}</p>
                    <p>• {{ $t('page.profile.capability.uploadTips.fileSizeLimit') }}</p>
                    <p>• {{ $t('page.profile.capability.uploadTips.tryTextInput') }}</p>
                  </div>
                </NAlert>
                <NUpload
                  :file-list="fileList"
                  @update:file-list="handleFileChange"
                  :max="1"
                  accept=".pdf,.doc,.docx,.txt"
                >
                  <NButton :loading="uploading">
                    <template #icon>
                      <span>⬆️</span>
                    </template>
                    {{ $t('page.profile.capability.selectFile') }}
                  </NButton>
                </NUpload>
                <NButton v-if="fileList.length > 0 && !uploadedFileUrl" type="primary" @click="handleFileUpload" :loading="uploading">
                  {{ $t('page.profile.capability.uploadFile') }}
                </NButton>
                <NTag v-if="uploadedFileUrl" type="success" class="ml-2">
                  <template #icon>
                    <span>✅</span>
                  </template>
                  {{ $t('page.profile.capability.uploadSuccess') }}
                </NTag>
              </div>
            </NTabPane>

            <!-- 纯文本输入 -->
            <NTabPane name="2" :tab="$t('page.profile.capability.textInput')">
              <div class="space-y-4">
                <p class="text-gray-600 text-sm">{{ $t('page.profile.capability.textInputDesc') }}</p>
                <NInput
                  v-model:value="rawTextInput"
                  type="textarea"
                  :placeholder="$t('page.profile.capability.textPlaceholder')"
                  :rows="8"
                  show-count
                  :maxlength="10000"
                />
              </div>
            </NTabPane>
          </NTabs>
        </div>

        <!-- 评分概览：核心评价 + 雷达图 + 维度进度条 -->
        <CapabilityScoreCard
          :dimensions="scoreDimensions"
          :total-score="profile.competitivenessScore || totalScore"
          :total-max="100"
          total-label="综合竞争力"
          :summary="profile.summary"
          :strengths="profile.strengths"
        />

        <!-- 能力详情（七维）：2 列卡片网格 -->
        <div class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
          <div class="flex items-center gap-2 mb-5">
            <span class="text-xl">🧩</span>
            <h3 class="text-lg font-bold text-slate-800">{{ $t('page.profile.capability.abilityDetail') }}</h3>
          </div>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div
              v-for="item in abilityItems"
              :key="item.key"
              class="group bg-gradient-to-br from-slate-50 to-white border border-slate-200 hover:border-blue-300 hover:shadow-md transition rounded-xl p-4"
            >
              <div class="flex items-center gap-2 mb-2">
                <span class="w-2 h-2 rounded-full bg-blue-500 group-hover:scale-125 transition"></span>
                <h4 class="font-semibold text-slate-800 text-sm">{{ item.label }}</h4>
              </div>
              <p v-if="item.value" class="text-slate-600 text-xs leading-relaxed">{{ item.value }}</p>
              <p v-else class="text-slate-300 text-xs italic">暂无相关信息</p>
            </div>
          </div>
        </div>

        <!-- 缺失技能 + 证据不足：双列对比 -->
        <div
          v-if="(profile.missingSkills && profile.missingSkills.length > 0) || (profile.weakEvidenceItems && profile.weakEvidenceItems.length > 0)"
          class="grid grid-cols-1 md:grid-cols-2 gap-6"
        >
          <!-- 缺失技能 -->
          <div v-if="profile.missingSkills && profile.missingSkills.length > 0"
               class="bg-gradient-to-br from-orange-50 to-white border border-orange-100 rounded-2xl p-6">
            <div class="flex items-center gap-2 mb-4">
              <span class="text-lg">⚠️</span>
              <h3 class="font-bold text-slate-800">{{ $t('page.profile.capability.missingSkills') }}</h3>
              <span class="ml-auto text-xs text-orange-600 bg-orange-100 px-2 py-0.5 rounded-full">
                {{ profile.missingSkills.length }}
              </span>
            </div>
            <ul class="space-y-2.5">
              <li v-for="(item, index) in profile.missingSkills" :key="index"
                  class="flex items-start gap-2 text-sm bg-white/60 rounded-lg px-3 py-2 border border-orange-100">
                <span class="text-orange-500 mt-0.5 flex-shrink-0">⊖</span>
                <span class="text-slate-700">{{ item }}</span>
              </li>
            </ul>
          </div>

          <!-- 证据不足 -->
          <div v-if="profile.weakEvidenceItems && profile.weakEvidenceItems.length > 0"
               class="bg-gradient-to-br from-rose-50 to-white border border-rose-100 rounded-2xl p-6">
            <div class="flex items-center gap-2 mb-4">
              <span class="text-lg">❓</span>
              <h3 class="font-bold text-slate-800">{{ $t('page.profile.capability.weakEvidence') }}</h3>
              <span class="ml-auto text-xs text-rose-600 bg-rose-100 px-2 py-0.5 rounded-full">
                {{ profile.weakEvidenceItems.length }}
              </span>
            </div>
            <ul class="space-y-2.5">
              <li v-for="(item, index) in profile.weakEvidenceItems" :key="index"
                  class="flex items-start gap-2 text-sm bg-white/60 rounded-lg px-3 py-2 border border-rose-100">
                <span class="text-rose-500 mt-0.5 flex-shrink-0">!</span>
                <span class="text-slate-700">{{ item }}</span>
              </li>
            </ul>
          </div>
        </div>

        <!-- 改进建议：带优先级色条 -->
        <div v-if="profile.suggestions && profile.suggestions.length > 0"
             class="bg-white p-6 rounded-2xl border border-slate-200 shadow-sm">
          <div class="flex items-center gap-2 mb-5">
            <span class="text-xl">💡</span>
            <h3 class="text-lg font-bold text-slate-800">{{ $t('page.profile.capability.suggestions') }}</h3>
            <span class="ml-auto text-xs text-slate-500 bg-slate-100 px-2 py-0.5 rounded-full">
              {{ profile.suggestions.length }} 条
            </span>
          </div>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div
              v-for="(item, index) in profile.suggestions"
              :key="index"
              class="relative bg-gradient-to-br from-slate-50 to-white border border-slate-200 rounded-xl p-4 pl-5 hover:shadow-md transition"
            >
              <!-- 左侧优先级色条 -->
              <div
                class="absolute left-0 top-3 bottom-3 w-1 rounded-r"
                :class="{
                  'bg-rose-500': item.priority === '高',
                  'bg-amber-500': item.priority === '中',
                  'bg-emerald-500': item.priority === '低'
                }"
              ></div>
              <div class="flex items-center gap-2 mb-2">
                <span
                  class="px-2 py-0.5 text-xs rounded-full font-medium"
                  :class="{
                    'bg-rose-100 text-rose-700': item.priority === '高',
                    'bg-amber-100 text-amber-700': item.priority === '中',
                    'bg-emerald-100 text-emerald-700': item.priority === '低'
                  }"
                >
                  {{ item.priority }}优先级
                </span>
                <span class="text-sm font-semibold text-slate-700">{{ item.category }}</span>
              </div>
              <div class="text-xs text-slate-600 mb-1.5 leading-relaxed">
                <span class="font-medium text-slate-700">问题：</span>{{ item.issue }}
              </div>
              <div class="text-xs text-slate-700 leading-relaxed">
                <span class="font-medium">建议：</span>{{ item.recommendation }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="max-w-4xl mx-auto text-center py-20 bg-white rounded-xl border border-gray-100 shadow-sm">
        <span class="text-6xl mb-4 mx-auto opacity-50 text-gray-400">📄</span>
        <p class="text-gray-400 mb-4">{{ $t('page.profile.capability.empty') }}</p>

        <!-- 生成方式选择 -->
        <div class="max-w-2xl mx-auto mb-6">
          <NTabs v-model:value="generateMethod" type="segment" class="mb-6">
            <NTabPane name="0" :tab="$t('page.profile.capability.autoGenerate')">
              <p class="text-gray-600 text-sm mb-4">{{ $t('page.profile.capability.autoGenerateDesc') }}</p>
            </NTabPane>

            <NTabPane name="1" :tab="$t('page.profile.capability.fileUpload')">
              <div class="space-y-4">
                <p class="text-gray-600 text-sm">{{ $t('page.profile.capability.fileUploadDesc') }}</p>
                <NUpload
                  :file-list="fileList"
                  @update:file-list="handleFileChange"
                  :max="1"
                  accept=".pdf,.doc,.docx,.txt"
                >
                  <NButton :loading="uploading">
                    <template #icon>
                      <span>⬆️</span>
                    </template>
                    {{ $t('page.profile.capability.selectFile') }}
                  </NButton>
                </NUpload>
                <NButton v-if="fileList.length > 0 && !uploadedFileUrl" type="primary" @click="handleFileUpload" :loading="uploading">
                  {{ $t('page.profile.capability.uploadFile') }}
                </NButton>
                <NTag v-if="uploadedFileUrl" type="success">
                  <template #icon>
                    <span>✅</span>
                  </template>
                  {{ $t('page.profile.capability.uploadSuccess') }}
                </NTag>
              </div>
            </NTabPane>

            <NTabPane name="2" :tab="$t('page.profile.capability.textInput')">
              <div class="space-y-4">
                <p class="text-gray-600 text-sm">{{ $t('page.profile.capability.textInputDesc') }}</p>
                <NInput
                  v-model:value="rawTextInput"
                  type="textarea"
                  :placeholder="$t('page.profile.capability.textPlaceholder')"
                  :rows="8"
                  show-count
                  :maxlength="10000"
                />
              </div>
            </NTabPane>
          </NTabs>

          <NButton type="primary" size="large" :loading="generating" @click="handleGenerate">
            <template #icon>
              <span>✨</span>
            </template>
            {{ $t('page.profile.capability.generate') }}
          </NButton>
        </div>
      </div>
    </NSpin>
  </div>
</template>

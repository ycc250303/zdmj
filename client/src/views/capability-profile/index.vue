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
const profile = ref<CapabilityProfileApi.StudentCapabilityProfile | null>(null);
const isLogin = computed(() => Boolean(authStore.token));

// 生成方式：'0'=自动生成（从简历数据）, '1'=文件上传, '2'=纯文本
// 使用字符串类型，因为 NTabs 的 name 属性是字符串
const generateMethod = ref<string>('0');

// 文件上传相关
const fileList = ref<any[]>([]);
const uploadedFileUrl = ref('');
/** 已上传到 COS 的本地文件标识，避免重复上传同一文件 */
const lastUploadedFileKey = ref('');

// 纯文本输入
const rawTextInput = ref('');

// 计算总分项
const totalScore = computed(() => {
  if (!profile.value?.scoreDetail) return 0;
  const { scoreDetail } = profile.value;
  return (
    (scoreDetail.projectExperienceScore || 0) +
    (scoreDetail.skillMatchScore || 0) +
    (scoreDetail.contentCompletenessScore || 0) +
    (scoreDetail.structureClarityScore || 0) +
    (scoreDetail.expressionProfessionalismScore || 0)
  );
});

// 能力项列表
const abilityItems = computed(() => {
  if (!profile.value) return [];
  return [
    { key: 'professionalSkills', label: '专业技能', icon: 'mdi:code-braces', value: profile.value.professionalSkills },
    { key: 'honorsAndAwards', label: '获奖经历', icon: 'mdi:trophy', value: profile.value.honorsAndAwards },
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

function getSelectedFile(): File | null {
  const fileItem = fileList.value[0];
  const file = (fileItem?.file || fileItem) as File | undefined;
  return file?.name ? file : null;
}

function getFileKey(file: File) {
  return `${file.name}:${file.size}:${file.lastModified}`;
}

function validateResumeFile(file: File): boolean {
  if (/[^\x00-\x7F]/.test(file.name)) {
    window.$message?.error($t('page.profile.capability.fileNameHasChinese'));
    return false;
  }
  const fileName = file.name.toLowerCase();
  const validExtensions = ['.pdf', '.txt', '.doc', '.docx'];
  if (!validExtensions.some(ext => fileName.endsWith(ext))) {
    window.$message?.error($t('page.profile.capability.onlySupportFormats'));
    return false;
  }
  return true;
}

/** 生成前确保简历文件已在 COS；若本地已选新文件则自动上传 */
async function ensureResumeFileOnCos(): Promise<string | null> {
  const file = getSelectedFile();

  if (file) {
    if (!validateResumeFile(file)) {
      return null;
    }
    const fileKey = getFileKey(file);
    if (uploadedFileUrl.value && lastUploadedFileKey.value === fileKey) {
      return uploadedFileUrl.value;
    }
    const { data, error } = await fetchUploadFile(file, 'profile');
    if (!error && data?.url) {
      uploadedFileUrl.value = data.url;
      lastUploadedFileKey.value = fileKey;
      return data.url;
    }
    window.$message?.error(
      $t('page.profile.capability.uploadFailed') + ': ' + (error?.message || $t('page.profile.capability.unknownError'))
    );
    return null;
  }

  if (uploadedFileUrl.value) {
    return uploadedFileUrl.value;
  }

  window.$message?.warning($t('page.profile.capability.selectFileFirst'));
  return null;
}

function handleFileChange(options: any) {
  const newFileList = Array.isArray(options) ? options : (options?.fileList || []);
  fileList.value = newFileList;
  if (newFileList.length === 0) {
    uploadedFileUrl.value = '';
    lastUploadedFileKey.value = '';
    return;
  }
  const file = getSelectedFile();
  if (file && getFileKey(file) !== lastUploadedFileKey.value) {
    uploadedFileUrl.value = '';
  }
}

/**
 * 生成能力画像
 */
async function handleGenerate() {
  if (generating.value) {
    return;
  }
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
      const pdfUrl = await ensureResumeFileOnCos();
      if (!pdfUrl) {
        return;
      }
      try {
        new URL(pdfUrl);
      } catch {
        window.$message?.error($t('page.profile.capability.urlInvalid'));
        return;
      }
      requestData = { pdfUrl };
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
      const backendMsg = (() => {
        const data = (error as { response?: { data?: { detail?: string; msg?: string } } })?.response?.data;
        return data?.detail || data?.msg;
      })();
      let errorMessage = $t('page.profile.capability.generateFailed');
      if (generateMethod.value === '1') {
        if (backendMsg?.includes('PDF')) {
          errorMessage = $t('page.profile.capability.pdfParseFailed');
        } else {
          errorMessage = `${$t('page.profile.capability.fileProcessFailed')}: ${backendMsg || error?.message || $t('page.profile.capability.unknownError')}`;
        }
      } else {
        errorMessage = `${errorMessage}: ${backendMsg || error?.message || $t('page.profile.capability.unknownError')}`;
      }
      window.$message?.error(errorMessage);
    }
  } catch (err: any) {
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
    { key: 'projectExperienceScore', label: '项目经验', score: sd.projectExperienceScore || 0, max: 40 },
    { key: 'skillMatchScore', label: '技能匹配', score: sd.skillMatchScore || 0, max: 20 },
    { key: 'contentCompletenessScore', label: '内容完整性', score: sd.contentCompletenessScore || 0, max: 15 },
    { key: 'structureClarityScore', label: '结构清晰度', score: sd.structureClarityScore || 0, max: 15 },
    { key: 'expressionProfessionalismScore', label: '表达专业性', score: sd.expressionProfessionalismScore || 0, max: 10 }
  ];
});

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="nova-page">
    <div v-if="!isLogin" class="nova-empty nova-empty--lg">
      <span class="nova-empty__icon">
        <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
          <rect x="4" y="11" width="16" height="10" rx="2" />
          <path d="M8 11V7a4 4 0 0 1 8 0v4" />
        </svg>
      </span>
      <p class="nova-empty__text">{{ $t('page.profile.capability.loginToView') }}</p>
      <NButton type="primary" @click="router.push('/login')">
        {{ $t('page.profile.capability.goToLogin') }}
      </NButton>
    </div>

    <NSpin v-else :show="loading">
      <div v-if="profile" class="nova-page__inner space-y-5">
        <header class="nova-page__head">
          <div>
            <span class="nova-eyebrow">// capability profile</span>
            <h1 class="nova-page__title font-display">{{ $t('page.profile.capability.title') }}</h1>
            <p class="nova-page__sub">{{ $t('page.profile.capability.desc') }}</p>
          </div>
          <NButton
            type="primary"
            size="medium"
            :loading="generating"
            :disabled="generating"
            @click="handleGenerate"
          >
            <template #icon>
              <icon-carbon-renew class="text-14px" />
            </template>
            {{ $t('page.profile.capability.regenerate') }}
          </NButton>
        </header>

        <section class="nova-panel">
          <h3 class="nova-panel__title">{{ $t('page.profile.capability.generateMethod') }}</h3>

          <NTabs v-model:value="generateMethod" type="segment">
            <NTabPane name="0" :tab="$t('page.profile.capability.autoGenerate')">
              <p class="nova-panel__hint">{{ $t('page.profile.capability.autoGenerateDesc') }}</p>
            </NTabPane>

            <NTabPane name="1" :tab="$t('page.profile.capability.fileUpload')">
              <div class="space-y-4">
                <p class="nova-panel__hint">{{ $t('page.profile.capability.fileUploadDesc') }}</p>
                <NAlert type="info" :bordered="false" class="text-sm">
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
                  <NButton>
                    <template #icon>
                      <icon-carbon-upload class="text-14px" />
                    </template>
                    {{ $t('page.profile.capability.selectFile') }}
                  </NButton>
                </NUpload>
                <p v-if="fileList.length > 0" class="nova-panel__hint">
                  {{ $t('page.profile.capability.uploadOnGenerateHint') }}
                </p>
              </div>
            </NTabPane>

            <NTabPane name="2" :tab="$t('page.profile.capability.textInput')">
              <div class="space-y-4">
                <p class="nova-panel__hint">{{ $t('page.profile.capability.textInputDesc') }}</p>
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
        </section>

        <!-- 评分概览：核心评价 + 雷达图 + 维度进度条 -->
        <CapabilityScoreCard
          :dimensions="scoreDimensions"
          :total-score="profile.competitivenessScore || totalScore"
          :total-max="100"
          total-label="综合竞争力"
          :summary="profile.summary"
          :strengths="profile.strengths"
        />

        <section class="nova-panel">
          <header class="nova-panel__head">
            <h3 class="nova-panel__title">{{ $t('page.profile.capability.abilityDetail') }}</h3>
          </header>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div v-for="item in abilityItems" :key="item.key" class="nova-ability">
              <div class="nova-ability__head">
                <span class="nova-ability__dot" />
                <h4 class="nova-ability__label">{{ item.label }}</h4>
              </div>
              <p v-if="item.value" class="nova-ability__value">{{ item.value }}</p>
              <p v-else class="nova-ability__empty">暂无相关信息</p>
            </div>
          </div>
        </section>

        <section v-if="profile.suggestions && profile.suggestions.length > 0" class="nova-panel">
          <header class="nova-panel__head">
            <h3 class="nova-panel__title">{{ $t('page.profile.capability.suggestions') }}</h3>
            <span class="nova-panel__count">{{ profile.suggestions.length }} 条</span>
          </header>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div
              v-for="(item, index) in profile.suggestions"
              :key="index"
              class="nova-suggestion"
              :data-priority="item.priority"
            >
              <span class="nova-suggestion__bar" />
              <div class="nova-suggestion__head">
                <span class="nova-suggestion__priority">{{ item.priority }}优先级</span>
                <span class="nova-suggestion__category">{{ item.category }}</span>
              </div>
              <div class="nova-suggestion__line">
                <span class="nova-suggestion__label">问题：</span>{{ item.issue }}
              </div>
              <div class="nova-suggestion__line">
                <span class="nova-suggestion__label">建议：</span>{{ item.recommendation }}
              </div>
            </div>
          </div>
        </section>
      </div>

      <div v-else class="nova-empty nova-empty--lg">
        <span class="nova-empty__icon">
          <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <path d="M14 2v6h6" />
          </svg>
        </span>
        <p class="nova-empty__text">{{ $t('page.profile.capability.empty') }}</p>

        <div class="max-w-2xl mx-auto w-full mb-2">
          <NTabs v-model:value="generateMethod" type="segment" class="mb-4">
            <NTabPane name="0" :tab="$t('page.profile.capability.autoGenerate')">
              <p class="nova-panel__hint">{{ $t('page.profile.capability.autoGenerateDesc') }}</p>
            </NTabPane>
            <NTabPane name="1" :tab="$t('page.profile.capability.fileUpload')">
              <div class="space-y-3">
                <p class="nova-panel__hint">{{ $t('page.profile.capability.fileUploadDesc') }}</p>
                <NUpload
                  :file-list="fileList"
                  @update:file-list="handleFileChange"
                  :max="1"
                  accept=".pdf,.doc,.docx,.txt"
                >
                  <NButton>
                    <template #icon>
                      <icon-carbon-upload class="text-14px" />
                    </template>
                    {{ $t('page.profile.capability.selectFile') }}
                  </NButton>
                </NUpload>
                <p v-if="fileList.length > 0" class="nova-panel__hint">
                  {{ $t('page.profile.capability.uploadOnGenerateHint') }}
                </p>
              </div>
            </NTabPane>
            <NTabPane name="2" :tab="$t('page.profile.capability.textInput')">
              <div class="space-y-3">
                <p class="nova-panel__hint">{{ $t('page.profile.capability.textInputDesc') }}</p>
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

          <NButton
            type="primary"
            size="medium"
            :loading="generating"
            :disabled="generating"
            @click="handleGenerate"
          >
            <template #icon>
              <icon-carbon-flash class="text-14px" />
            </template>
            {{ $t('page.profile.capability.generate') }}
          </NButton>
        </div>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.nova-page {
  position: relative;
  min-height: 100%;
  padding: 32px 32px 60px;
  color: var(--nova-text);
}

.nova-page__inner {
  max-width: 1080px;
  margin: 0 auto;
}

.nova-page__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.nova-page__title {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(120deg, #fff 0%, #c9c4ff 60%, #93f1ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.nova-page__sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--nova-text-faded);
  max-width: 640px;
  line-height: 1.6;
}

.nova-eyebrow {
  display: inline-block;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  letter-spacing: 0.16em;
  color: var(--nova-violet);
  text-transform: lowercase;
}

.nova-panel {
  padding: 22px 24px;
  border-radius: 16px;
  border: 1px solid var(--nova-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0.018));
  backdrop-filter: blur(20px) saturate(1.05);
}

.nova-panel__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.nova-panel__title {
  margin: 0;
  font-size: 15.5px;
  font-weight: 600;
  color: #fff;
  letter-spacing: -0.005em;
}

.nova-panel__count {
  margin-left: auto;
  padding: 2px 9px;
  border-radius: 999px;
  background: rgba(124, 92, 255, 0.12);
  border: 1px solid rgba(124, 92, 255, 0.28);
  color: var(--nova-violet);
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  letter-spacing: 0.04em;
}

.nova-panel__hint {
  margin: 0;
  font-size: 13px;
  color: var(--nova-text-faded);
  line-height: 1.6;
}

.nova-ability {
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--nova-border);
  background: rgba(255, 255, 255, 0.025);
  transition: border-color 0.25s ease, transform 0.25s ease;
}

.nova-ability:hover {
  border-color: var(--nova-border-strong);
  transform: translateY(-1px);
}

.nova-ability__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.nova-ability__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--nova-violet);
  box-shadow: 0 0 8px var(--nova-violet);
}

.nova-ability__label {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: #fff;
}

.nova-ability__value {
  margin: 0;
  font-size: 12.5px;
  color: var(--nova-text-soft);
  line-height: 1.65;
}

.nova-ability__empty {
  margin: 0;
  font-size: 12.5px;
  color: var(--nova-text-faded);
  font-style: italic;
}

.nova-suggestion {
  position: relative;
  padding: 14px 16px 14px 18px;
  border-radius: 12px;
  border: 1px solid var(--nova-border);
  background: rgba(255, 255, 255, 0.025);
  overflow: hidden;
  transition: border-color 0.25s ease, transform 0.25s ease;
}

.nova-suggestion:hover {
  border-color: var(--nova-border-strong);
  transform: translateY(-1px);
}

.nova-suggestion__bar {
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--nova-mint);
}

.nova-suggestion[data-priority="高"] .nova-suggestion__bar { background: var(--nova-coral); }
.nova-suggestion[data-priority="中"] .nova-suggestion__bar { background: #f5b969; }
.nova-suggestion[data-priority="低"] .nova-suggestion__bar { background: var(--nova-mint); }

.nova-suggestion__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.nova-suggestion__priority {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-family: 'JetBrains Mono', monospace;
  letter-spacing: 0.04em;
}

.nova-suggestion[data-priority="高"] .nova-suggestion__priority {
  background: rgba(251, 113, 133, 0.14);
  color: var(--nova-coral);
  border: 1px solid rgba(251, 113, 133, 0.32);
}
.nova-suggestion[data-priority="中"] .nova-suggestion__priority {
  background: rgba(245, 185, 105, 0.12);
  color: #f5b969;
  border: 1px solid rgba(245, 185, 105, 0.32);
}
.nova-suggestion[data-priority="低"] .nova-suggestion__priority {
  background: rgba(52, 211, 153, 0.12);
  color: var(--nova-mint);
  border: 1px solid rgba(52, 211, 153, 0.32);
}

.nova-suggestion__category {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
}

.nova-suggestion__line {
  font-size: 12.5px;
  color: var(--nova-text-soft);
  line-height: 1.6;
  margin-bottom: 4px;
}

.nova-suggestion__label {
  color: var(--nova-text);
  font-weight: 600;
}

.nova-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  border-radius: 18px;
  border: 1px dashed var(--nova-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.025), rgba(255, 255, 255, 0.008));
  text-align: center;
  gap: 14px;
  max-width: 720px;
  margin: 0 auto;
}

.nova-empty--lg {
  padding: 72px 28px;
}

.nova-empty__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: rgba(124, 92, 255, 0.1);
  border: 1px solid rgba(124, 92, 255, 0.22);
  color: var(--nova-violet);
}

.nova-empty__text {
  color: var(--nova-text-faded);
  font-size: 13.5px;
  margin: 0;
}
</style>

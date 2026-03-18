<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { fetchGetResumeList, fetchDeleteResume } from '@/service/api/resume';
import { $t } from '@/locales';

defineOptions({ name: 'resumes' });

const router = useRouter();

// 存储真实的简历列表数据
const resumeList = ref<any[]>([]);
const loading = ref(false);

// 初始化：从后端拉取真实列表
async function loadResumeList() {
  loading.value = true;
  try {
    const { data, error } = await fetchGetResumeList();
    if (!error && data) {
      resumeList.value = data || [];
    }
  } catch (err) {
    window.$message?.error('获取简历列表失败，请检查后端服务');
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadResumeList();
});

// 新建空白简历
function handleCreateBlank() {
  router.push({ name: 'resumes-editor' });
}

// 去编辑简历
function handleEdit(id: number) {
  router.push({ name: 'resumes-editor', query: { id } });
}

// 触发简历解析
function handleUploadAndParse() {
  window.$message?.info('智能解析导入功能开发中~');
}

// 真实调用后端删除接口
function handleDelete(id: number) {
  window.$dialog?.warning({
    title: '删除确认',
    content: '确定要删除这份精心准备的简历吗？操作不可逆。',
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      loading.value = true;
      const { error } = await fetchDeleteResume(id);
      loading.value = false;
      
      if (!error) {
        window.$message?.success('删除成功');
        loadResumeList(); // 删除成功后重新拉取最新列表
      } else {
        window.$message?.error('删除失败，请稍后重试');
      }
    }
  });
}
</script>

<template>
  <div class="h-full p-6 bg-slate-50/50" v-loading="loading">
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-2xl font-bold text-slate-800">我的简历库</h1>
        <p class="text-slate-500 mt-1 text-sm">管理您的所有简历版本，支持 AI 智能解析与深度定制。</p>
      </div>
      <div class="flex gap-3">
        <NButton type="primary" size="large" secondary @click="handleUploadAndParse">
          <template #icon>
            <div class="i-mdi-file-upload-outline"></div>
          </template>
          智能解析导入
        </NButton>
        <NButton type="primary" size="large" @click="handleCreateBlank">
          <template #icon>
            <div class="i-mdi-plus"></div>
          </template>
          新建空白简历
        </NButton>
      </div>
    </div>

    <NGrid :x-gap="24" :y-gap="24" cols="1 s:2 m:3 lg:4" responsive="screen">
      
      <NGridItem v-for="resume in resumeList" :key="resume.id">
        <NCard 
          hoverable 
          class="h-full rounded-2xl cursor-pointer border-slate-200 transition-all hover:-translate-y-1 hover:shadow-md"
          content-style="padding: 0;"
          @click="handleEdit(resume.id)"
        >
          <div class="h-32 bg-gradient-to-br from-blue-50 to-indigo-50 p-5 flex flex-col justify-between border-b border-slate-100">
            <div class="flex justify-between items-start">
              <div class="bg-white/80 text-blue-600 text-xs font-bold px-3 py-1 rounded-full backdrop-blur-sm border border-blue-100">
                SmartHire 简历
              </div>
              <NButton quaternary circle size="small" @click.stop="handleDelete(resume.id)">
                <template #icon>
                  <div class="i-mdi-delete-outline text-slate-400 hover:text-red-500"></div>
                </template>
              </NButton>
            </div>
            <h2 class="text-lg font-bold text-slate-800 line-clamp-2">{{ resume.name || '未命名简历' }}</h2>
          </div>
          
          <div class="p-4 flex items-center justify-between bg-white">
            <div class="flex items-center gap-2 text-xs text-slate-500">
              <div class="i-mdi-file-document-outline text-slate-400"></div>
              包含 {{ resume.projects?.length || 0 }} 个项目
            </div>
            <div class="text-blue-500 text-sm font-medium flex items-center gap-1 group-hover:text-blue-600">
              去编辑 <div class="i-mdi-chevron-right"></div>
            </div>
          </div>
        </NCard>
      </NGridItem>

      <NGridItem v-if="resumeList.length === 0 && !loading">
        <NCard class="h-full rounded-2xl border-dashed border-2 border-slate-300 bg-slate-50 flex items-center justify-center min-h-[220px]">
          <NEmpty description="还没有简历，快去创建第一份吧！">
            <template #extra>
              <NButton type="primary" size="small" @click="handleCreateBlank">立即创建</NButton>
            </template>
          </NEmpty>
        </NCard>
      </NGridItem>

    </NGrid>
  </div>
</template>
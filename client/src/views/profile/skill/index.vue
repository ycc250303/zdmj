<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { fetchGetSkillList, fetchDeleteSkill } from '@/service/api/resume';
import type { ResumeApi } from '@/service/api/resume';
import { $t } from '@/locales';
import SkillForm from './components/SkillForm.vue';

const isEditing = ref(false);
const skillList = ref<ResumeApi.SkillSet[]>([]);
const currentEditData = ref<ResumeApi.SkillSet | undefined>(undefined);
const loading = ref(true);

async function loadData() { loading.value = true; try { const res = await fetchGetSkillList(); skillList.value = res.data || []; } finally { loading.value = false; } }
function handleAddNew() { currentEditData.value = undefined; isEditing.value = true; }
function handleEdit(item: ResumeApi.SkillSet) { currentEditData.value = { ...item }; isEditing.value = true; }
async function handleDelete(id: number) { const { error } = await fetchDeleteSkill(id); if (!error) { window.$message?.success($t('page.profile.skill.deleteSuccess')); loadData(); } }
function onFormSuccess() { isEditing.value = false; loadData(); }
onMounted(() => { loadData(); });
</script>

<template>
  <div class="editorial-page">
    <div v-if="!isEditing" class="editorial-wrap">
      <header class="section-head">
        <div class="head-meta"><span class="head-bar"></span><span class="head-tag">— PROFILE / SKILLS —</span></div>
        <div class="head-row">
          <h1 class="head-title font-display">{{ $t('page.profile.skill.title') }}</h1>
          <button class="primary-btn" @click="handleAddNew"><span class="plus">+</span>{{ $t('page.profile.skill.addBtn') }}</button>
        </div>
      </header>
      <div class="head-rule"></div>
      <NSpin :show="loading">
        <div v-if="skillList.length === 0" class="empty-block">
          <div class="empty-no font-display">∅</div>
          <p class="empty-headline font-display">No entries yet.</p>
          <p class="empty-sub">{{ $t('page.profile.common.empty') }}</p>
        </div>
        <div v-else class="items-list">
          <article v-for="(item, idx) in skillList" :key="item.id" class="list-row">
            <div class="row-no font-display">{{ String(idx + 1).padStart(2, '0') }}</div>
            <div class="row-content">
              <h3 class="row-title">{{ item.name }}</h3>
              <div class="skill-groups" v-if="item.content?.length">
                <div v-for="(sg, gi) in item.content" :key="gi" class="skill-group">
                  <span class="sg-label">{{ sg.type }}</span>
                  <div class="sg-tags"><span v-for="tag in sg.content" :key="tag" class="sg-tag">{{ tag }}</span></div>
                </div>
              </div>
            </div>
            <div class="row-actions">
              <button class="row-btn" @click="handleEdit(item)">{{ $t('page.profile.common.edit') }}<span class="arrow">→</span></button>
              <NPopconfirm @positive-click="handleDelete(item.id)"><template #trigger><button class="row-btn danger">{{ $t('page.profile.common.delete') }}</button></template>{{ $t('page.profile.common.confirmDelete') }}</NPopconfirm>
            </div>
          </article>
        </div>
      </NSpin>
    </div>
    <div v-else class="editorial-wrap py-4"><SkillForm :initial-data="currentEditData" @success="onFormSuccess" @cancel="isEditing = false" /></div>
  </div>
</template>

<style scoped>
.editorial-page { min-height: 100%; background: #fefefe; padding: 40px 56px 56px; overflow: auto; }
.editorial-wrap { max-width: 900px; }
.section-head { margin-bottom: 16px; }
.head-meta { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.head-bar { width: 40px; height: 2px; background: #c4a46c; }
.head-tag { font-size: 11px; letter-spacing: 0.22em; color: #6a6a6a; }
.head-row { display: flex; justify-content: space-between; align-items: center; gap: 24px; }
.head-title { font-size: clamp(32px, 4vw, 48px); font-weight: 600; letter-spacing: -0.02em; color: #1a1a1a; }
.head-rule { height: 1px; background: #e0e0e0; margin-bottom: 40px; }
.head-rule::after { content: ''; display: block; height: 1px; background: #e0e0e0; margin-top: 4px; }
.primary-btn { display: inline-flex; align-items: center; gap: 8px; padding: 10px 20px; border-radius: 999px; background: #c4a46c; color: #fff; border: none; font-size: 14px; font-weight: 500; cursor: pointer; transition: all 0.25s ease; }
.primary-btn:hover { background: #a08050; transform: translateY(-1px); }
.plus { font-size: 18px; }
.empty-block { text-align: center; padding: 80px 24px; }
.empty-no { font-size: 72px; color: #c4a46c; opacity: 0.3; }
.empty-headline { font-size: 24px; color: #1a1a1a; margin: 16px 0 8px; }
.empty-sub { color: #6a6a6a; }
.items-list { display: flex; flex-direction: column; }
.list-row { display: grid; grid-template-columns: 48px 1fr auto; gap: 20px; align-items: flex-start; padding: 20px 0; border-bottom: 1px solid #ebebeb; transition: background 0.25s; }
.list-row:hover { background: rgba(196,164,108,0.03); }
.list-row:first-child { border-top: 1px solid #ebebeb; }
.row-no { font-size: 18px; color: #c4a46c; font-style: italic; padding-top: 2px; }
.row-title { font-size: 17px; font-weight: 600; color: #1a1a1a; }
.skill-groups { margin-top: 10px; display: flex; flex-direction: column; gap: 8px; }
.skill-group { display: flex; align-items: flex-start; gap: 10px; }
.sg-label { font-size: 12px; font-weight: 600; color: #888; min-width: 64px; padding-top: 2px; }
.sg-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.sg-tag { font-size: 12px; padding: 2px 10px; border-radius: 999px; background: rgba(196,164,108,0.08); color: #8a7040; border: 1px solid rgba(196,164,108,0.2); }
.row-actions { display: flex; gap: 6px; align-self: center; }
.row-btn { display: inline-flex; align-items: center; gap: 6px; padding: 6px 16px; border-radius: 999px; border: 1px solid #ddd; background: #fff; color: #333; font-size: 13px; cursor: pointer; transition: all 0.22s ease; }
.row-btn:hover { border-color: #c4a46c; color: #c4a46c; }
.row-btn .arrow { transition: transform 0.22s; }
.row-btn:hover .arrow { transform: translateX(2px); }
.row-btn.danger { color: #c44536; border-color: rgba(196,68,54,0.3); }
.row-btn.danger:hover { background: #fef2f2; border-color: #c44536; }
</style>

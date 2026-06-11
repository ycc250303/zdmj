<script setup lang="ts">
import type { JobApi } from '@/service/api/job';
import { $t } from '@/locales';
import CapabilityDimension, { type AbilityItem } from '@/components/common/CapabilityDimension.vue';
import { NButton, NEmpty } from 'naive-ui';

defineOptions({ name: 'JobCapabilitySidebar' });

interface Props {
  capabilityProfile: JobApi.JobCapabilityProfile | null;
  generatingProfile: boolean;
}

defineProps<Props>();

const emit = defineEmits<{
  generate: [];
}>();

function buildAbilityItems(profile: JobApi.JobCapabilityProfile): AbilityItem[] {
  return [
    { key: 'professionalSkills', label: '专业技能', value: profile.professionalSkills, category: 'hard' as const },
    { key: 'certificates', label: '证书', value: profile.certificates, category: 'hard' as const },
    { key: 'practicalAbility', label: '实践能力', value: profile.practicalAbility, category: 'hard' as const },
    { key: 'innovationAbility', label: '创新能力', value: profile.innovationAbility, category: 'soft' as const },
    { key: 'learningAbility', label: '学习能力', value: profile.learningAbility, category: 'soft' as const },
    { key: 'pressureResistance', label: '抗压能力', value: profile.pressureResistance, category: 'soft' as const },
    { key: 'communicationAbility', label: '沟通能力', value: profile.communicationAbility, category: 'soft' as const },
  ].filter(item => item.value);
}
</script>

<template>
  <div class="airbnb-card p-5 h-full">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-base font-semibold text-[#222222] dark:text-gray-200">{{ $t('page.jobs.capabilityProfile') }}</h3>
      <NButton text size="small" type="primary" :loading="generatingProfile" @click="emit('generate')">
        刷新
      </NButton>
    </div>

    <div v-if="capabilityProfile" class="space-y-5">
      <!-- 岗位类型 -->
      <div v-if="capabilityProfile.targetRoleType">
        <div class="text-xs font-medium text-[#6a6a6a] dark:text-gray-400 mb-1">{{ $t('page.jobs.jobName') }}{{ $t('page.jobs.jobType') }}</div>
        <div class="text-sm text-[#222222] dark:text-gray-200">{{ capabilityProfile.targetRoleType }}</div>
      </div>

      <!-- 能力维度 -->
      <div v-if="buildAbilityItems(capabilityProfile).length">
        <CapabilityDimension :items="buildAbilityItems(capabilityProfile)" />
      </div>

      <!-- 岗位优势 -->
      <div v-if="capabilityProfile.strengths && capabilityProfile.strengths.length > 0">
        <div class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-2">{{ $t('page.jobs.strengths') }}</div>
        <ul class="space-y-1">
          <li v-for="(s, idx) in capabilityProfile.strengths" :key="idx" class="text-sm text-[#3f3f3f] dark:text-gray-300 flex items-start gap-2">
            <span class="text-[#c4a46c] shrink-0">+</span>
            <span>{{ s }}</span>
          </li>
        </ul>
      </div>

      <!-- 缺失技能 -->
      <div v-if="capabilityProfile.missingSkills && capabilityProfile.missingSkills.length > 0">
        <div class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-2">{{ $t('page.jobs.missingSkills') }}</div>
        <ul class="space-y-1">
          <li v-for="(s, idx) in capabilityProfile.missingSkills" :key="idx" class="text-sm text-[#3f3f3f] dark:text-gray-300 flex items-start gap-2">
            <span class="text-[#c4a46c] shrink-0">-</span>
            <span>{{ s }}</span>
          </li>
        </ul>
      </div>

      <!-- 证据不足 -->
      <div v-if="capabilityProfile.weakEvidenceItems && capabilityProfile.weakEvidenceItems.length > 0">
        <div class="text-sm font-semibold text-[#222222] dark:text-gray-200 mb-2">{{ $t('page.jobs.weakEvidenceItems') }}</div>
        <ul class="space-y-1">
          <li v-for="(item, idx) in capabilityProfile.weakEvidenceItems" :key="idx" class="text-sm text-[#3f3f3f] dark:text-gray-300 flex items-start gap-2">
            <span class="text-[#c4a46c] shrink-0">!</span>
            <span>{{ item }}</span>
          </li>
        </ul>
      </div>

      <!-- 总结 -->
      <div v-if="capabilityProfile.summary" class="bg-[#f7f7f7] dark:bg-gray-700/40 rounded-lg p-3">
        <div class="text-xs font-semibold text-[#222222] dark:text-gray-200 mb-1">{{ $t('page.jobs.summary') }}</div>
        <p class="text-sm text-[#3f3f3f] dark:text-gray-300 italic">{{ capabilityProfile.summary }}</p>
      </div>
    </div>

    <NEmpty v-else :description="$t('page.jobs.noProfile')" class="py-8">
      <template #extra>
        <NButton type="primary" :loading="generatingProfile" @click="emit('generate')">
          {{ $t('page.jobs.generateProfile') }}
        </NButton>
      </template>
    </NEmpty>
  </div>
</template>

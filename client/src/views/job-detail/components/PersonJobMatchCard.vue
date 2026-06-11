<script setup lang="ts">
import { computed } from 'vue';
import type { MatchApi } from '@/service/api/match';
import { $t } from '@/locales';
import CapabilityScoreCard, { type Dimension } from '@/components/common/CapabilityScoreCard.vue';

defineOptions({ name: 'PersonJobMatchCard' });

interface Props {
  matchResult: MatchApi.JobStudentMatch;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  generateMatch: [];
}>();

const matchDimensions = computed<Dimension[]>(() => {
  const dims = props.matchResult?.dimensions;
  if (!dims) return [];
  const labels: Record<string, string> = {
    basic: $t('page.jobs.matchDimensions.basic'),
    professionalSkill: $t('page.jobs.matchDimensions.professionalSkill'),
    professionalQuality: $t('page.jobs.matchDimensions.professionalQuality'),
    developmentPotential: $t('page.jobs.matchDimensions.developmentPotential')
  };
  return (Object.keys(labels) as Array<keyof typeof labels>)
    .map(key => ({
      key,
      label: labels[key],
      score: dims[key]?.score || 0,
      max: 100
    }));
});
</script>

<template>
  <div class="mb-6">
    <CapabilityScoreCard
      :dimensions="matchDimensions"
      :total-score="matchResult.overallScore || 0"
      :total-max="100"
      :total-label="$t('page.jobs.overallMatchScore')"
      :summary-label="$t('page.jobs.matchSummaryLabel')"
      :summary="matchResult.summary"
      :strengths-label="$t('page.jobs.matchHighlightsLabel')"
      :strengths="matchResult.matchedHighlights"
    />
  </div>
</template>

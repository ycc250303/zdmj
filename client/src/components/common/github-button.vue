<script setup lang="ts">
import { computed } from 'vue';
import { $t } from '@/locales';

defineOptions({
  name: 'GithubButton'
});

interface Props {
  /** Show tooltip */
  showTooltip?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showTooltip: true
});

const GITHUB_REPO_URL = 'https://github.com/ycc250303/zdmj';

const tooltipContent = computed(() => (props.showTooltip ? $t('icon.github') : ''));

function openGithubRepo() {
  window.open(GITHUB_REPO_URL, '_blank', 'noopener,noreferrer');
}
</script>

<template>
  <ButtonIcon icon="mdi:github" :tooltip-content="tooltipContent" @click="openGithubRepo" />
</template>

<style scoped></style>

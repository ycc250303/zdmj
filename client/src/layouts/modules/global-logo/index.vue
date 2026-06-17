<script setup lang="ts">
import { $t } from '@/locales';

defineOptions({
  name: 'GlobalLogo'
});

interface Props {
  /** Whether to show the title */
  showTitle?: boolean;
}

withDefaults(defineProps<Props>(), {
  showTitle: true
});
</script>

<template>
  <RouterLink to="/" class="nova-logo-link">
    <span class="nova-logo-mark">
      <SystemLogo class="text-20px" />
    </span>
    <span v-show="showTitle" class="nova-logo-name">
      {{ $t('system.title') }}
    </span>
  </RouterLink>
</template>

<style scoped>
.nova-logo-link {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px;
  width: 100%;
  white-space: nowrap;
  overflow: hidden;
}

.nova-logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  flex-shrink: 0;
  background: linear-gradient(135deg, rgba(124, 92, 255, 0.4), rgba(34, 211, 238, 0.32));
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.08), 0 8px 22px -8px rgba(124, 92, 255, 0.7);
  color: #fff;
}

.nova-logo-name {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  background: linear-gradient(120deg, #ffffff 0%, #c9c4ff 55%, #93f1ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
}
</style>

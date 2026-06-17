<script setup lang="ts">
import { computed } from 'vue';
import { useAppStore } from '@/store/modules/app';
import { $t } from '@/locales';

defineOptions({
  name: 'GlobalFooter'
});

const appStore = useAppStore();
const isZh = computed(() => appStore.locale === 'zh-CN');

const year = new Date().getFullYear();
</script>

<template>
  <DarkModeContainer class="nova-footer h-full flex-y-center justify-center gap-12px">
    <span class="nova-footer__brand font-display">{{ $t('system.title') }}</span>
    <span class="nova-footer__sep"></span>
    <span class="nova-footer__note">
      {{
        isZh
          ? `© ${year} "职"点迷津 · 面向软件工程大学生的智能求职导航`
          : `© ${year} "ZhiDian" · Career navigation for software engineering students`
      }}
    </span>
  </DarkModeContainer>
</template>

<style scoped>
.nova-footer__brand {
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 0.06em;
  background: linear-gradient(120deg, #c9c4ff 0%, #93f1ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
}

.nova-footer__sep {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: rgba(124, 92, 255, 0.6);
}

.nova-footer__note {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
  letter-spacing: 0.02em;
}

:root:not(.dark) .nova-footer__note {
  color: rgba(31, 31, 31, 0.55);
}
:root:not(.dark) .nova-footer__sep {
  background: rgba(124, 92, 255, 0.5);
}
</style>
